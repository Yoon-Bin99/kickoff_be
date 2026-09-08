package com.kickoff.be.chat.service;

import com.kickoff.be.chat.dto.ChatMessageResponse;
import com.kickoff.be.chat.dto.ChatResponse;
import com.kickoff.be.chat.dto.ChatRoomResponse;
import com.kickoff.be.chat.dto.ChatSendRequest;
import com.kickoff.be.chat.entity.ChatLeave;
import com.kickoff.be.chat.entity.ChatMessage;
import com.kickoff.be.chat.repository.ChatLeaveRepository;
import com.kickoff.be.chat.repository.ChatMessageRepository;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.push.dto.ChatPushEvent;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.dto.TeamSummary;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 채팅 (계약서 §6-1, v1.12.0 / 탭·나가기는 v1.13.0).
 *
 * 방 리소스가 따로 없다 — 수락된 신청 하나가 곧 방이다. 참여자는 <b>두 팀의 주장뿐</b>이고
 * ADMIN·MEMBER 는 못 들어온다. 일정 조율 채널이라 1:1 로 못박은 사용자 결정이다.
 *
 * 검사 순서는 리뷰(§7)와 같이 <b>권한 → 상태</b>다. 제3자에게는 그 매칭이 수락됐는지조차
 * 알려줄 이유가 없다.
 *
 * 전송은 matchAt 까지만이고 그 뒤로는 읽기 전용으로 남는다 — 계좌·장소처럼 주고받은 정보를
 * 나중에 다시 볼 수 있어야 해서 지우지 않는다.
 *
 * <b>나가기(v1.13.0)는 채팅에만 닿는다.</b> 매칭·리뷰·전적은 그대로다. 나간 쪽에게는 그
 * 시점 이전이 영구히 안 보이고, 남은 쪽에게는 SYSTEM 안내 한 줄이 남는다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    /** 나가기 기록이 없는 팀의 워터마크. 메시지 id 는 1 부터라 아무것도 가리지 않는다. */
    private static final long NO_WATERMARK = 0L;

    /**
     * 정렬: 마지막 메시지 최신순, 메시지 없는 방은 그 뒤에 <b>신청의 마지막 상태
     * 변경(updatedAt) 최신순</b> (계약서 §6-1).
     *
     * acceptedAt 컬럼을 두지 않는 건 계약이 확정한 선택이다 — 수락이 보통 마지막 변경이라
     * 사실상 수락 순이고, 입금 확인이 있으면 그만큼 위로 온다. updatedAt 은 응답에 없으므로
     * 목록을 만들기 전에 미리 정렬해 두고, 여기서는 앞쪽(메시지 있는 방)만 다시 줄 세운다 —
     * 자바의 정렬은 안정적이라 뒤쪽의 기존 순서가 유지된다.
     *
     * <b>동률에는 requestId 를 2차 키로 쓴다</b> (계약서 §6-1, v1.25.1 명문화). 시각만으로
     * 줄을 세우면 두 방의 마지막 메시지가 같은 시각을 받았을 때 순서가 정해지지 않는다 —
     * 그러면 <b>같은 목록을 다시 불러도 순서가 바뀐다.</b> 리뷰 목록이 v1.2.2 에서 같은
     * 이유로 id DESC 를 2차 키로 받았고, 여기만 빠져 있었다.
     *
     * 시각이 붙는 일이 드물어 보이지만 실제로 일어난다. 저장 정밀도(마이크로초)와 시계
     * 해상도가 겹치면 연속된 두 요청이 같은 값을 받는다.
     */
    private static final Comparator<ChatRoomResponse> ROOM_ORDER = Comparator
            .comparing((ChatRoomResponse room) -> room.lastMessage() != null)
            .reversed()
            .thenComparing(room -> room.lastMessage() == null
                            ? OffsetDateTime.MIN : room.lastMessage().createdAt(),
                    Comparator.reverseOrder())
            .thenComparing(ChatRoomResponse::requestId, Comparator.reverseOrder());

    private final ChatMessageRepository chatMessageRepository;
    private final ChatLeaveRepository chatLeaveRepository;
    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 방 조회 (계약서 §6-1).
     *
     * {@code after} 가 없으면 <b>최신</b> limit 개를 오름차순으로 준다(첫 로드), 있으면 그
     * 이후만 준다(폴링). 어느 쪽이든 응답은 오래된 것부터다 — FE 가 그대로 이어 붙인다.
     *
     * 나갔다 재입장한 경우 나간 시점 이전은 <b>여기서</b> 걸러 나간다. 계약서가 "FE 가 거를
     * 필요 없음"이라고 못박은 부분이라, 커서는 {@code after} 와 워터마크 중 큰 쪽이다.
     */
    @Transactional(readOnly = true)
    public ChatResponse messages(Long requestId, User user, Long after, Integer limit) {
        MatchRequest request = requireParticipant(requestId, user);
        Team myTeam = teamOf(request, user);
        long floor = Math.max(after == null ? NO_WATERMARK : after, watermarkOf(requestId, myTeam));
        Pageable page = PageRequest.ofSize(clampLimit(limit));

        List<ChatMessage> messages;
        if (after == null) {
            // 최신부터 limit 개를 받아 뒤집는다. 오름차순으로 자르면 방의 가장 오래된
            // 대화만 나와서, 대화가 길어질수록 화면이 엉뚱해진다.
            messages = new ArrayList<>(chatMessageRepository
                    .findByRequest_IdAndIdGreaterThanOrderByIdDesc(requestId, floor, page));
            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository
                    .findByRequest_IdAndIdGreaterThanOrderByIdAsc(requestId, floor, page);
        }
        return new ChatResponse(messages.stream().map(ChatMessageResponse::of).toList(),
                isOpen(request));
    }

    @Transactional
    public ChatMessageResponse send(Long requestId, User user, ChatSendRequest request) {
        MatchRequest matchRequest = requireParticipant(requestId, user);
        if (!isOpen(matchRequest)) {
            throw new BusinessException(ErrorCode.CHAT_CLOSED);
        }
        Team senderTeam = teamOf(matchRequest, user);
        ChatMessage saved = chatMessageRepository
                .save(ChatMessage.text(matchRequest, senderTeam, request.content()));

        // 전송이 곧 복귀다 (계약서 §6-1). 나갔던 방에 말을 걸어 놓고 그 방이 내 목록에
        // 없으면 상대의 답을 못 본다.
        chatLeaveRepository.findByRequest_IdAndTeam_Id(requestId, senderTeam.getId())
                .ifPresent(ChatLeave::rejoin);

        publish(matchRequest, senderTeam, request.content());
        return ChatMessageResponse.of(saved);
    }

    /**
     * 나가기 (계약서 §6-1, v1.13.0). 이미 나가 있으면 아무것도 하지 않는다 — 멱등이다.
     *
     * <b>안내 줄을 먼저 저장하고 그 id 를 워터마크로 삼는 순서가 핵심이다.</b> 반대로 하면
     * 안내 줄의 id 가 워터마크보다 커져서, 나간 본인이 재입장했을 때 "상대 팀이 채팅방을
     * 나갔습니다"를 보게 된다. 자기가 나간 사실이 상대가 나간 것처럼 보이는 화면이다.
     */
    @Transactional
    public void leave(Long requestId, User user) {
        MatchRequest request = requireParticipant(requestId, user);
        Team myTeam = teamOf(request, user);

        ChatLeave leave = chatLeaveRepository
                .findByRequest_IdAndTeam_Id(requestId, myTeam.getId())
                .orElse(null);
        if (leave != null && leave.isHidden()) {
            return;
        }
        ChatMessage notice = chatMessageRepository.save(ChatMessage.leaveNotice(request));
        if (leave == null) {
            leave = chatLeaveRepository.save(ChatLeave.of(request, myTeam));
        }
        leave.leaveAt(notice.getId());
    }

    /**
     * 채팅 탭의 방 목록 (계약서 §6-1, v1.13.0). 팀이 없으면 빈 배열이다.
     *
     * 페이징이 없어도 쿼리는 상수 개다 — 방 목록 1, 나가기 상태 1, 마지막 메시지 id 1,
     * 그 메시지 본문 1, 상대 팀 평점 1. 방마다 마지막 메시지를 물으면 목록이 길어질수록
     * 쿼리가 늘어난다.
     */
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> myChats(User user) {
        List<MatchRequest> rooms = requestRepository.findAcceptedRoomsOf(user.getId());
        if (rooms.isEmpty()) {
            return List.of();
        }

        Map<Long, Team> myTeams = new HashMap<>();
        rooms.forEach(room -> myTeams.put(room.getId(), teamOf(room, user)));
        Map<String, ChatLeave> leaves = leavesOf(myTeams.values());

        // 메시지 없는 방의 순서를 여기서 미리 잡아 둔다 (수락 최신순). 뒤의 정렬은
        // 안정적이라 이 순서가 그대로 남는다.
        List<MatchRequest> visible = rooms.stream()
                .filter(room -> !isHidden(leaves, room, myTeams.get(room.getId())))
                .sorted(Comparator.comparing(MatchRequest::getUpdatedAt).reversed())
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }

        Map<Long, ChatMessage> lastMessages = lastVisibleMessages(visible, myTeams, leaves);
        Map<Long, ReviewStats> stats = reviewRepository.statsMapOf(visible.stream()
                .map(room -> otherTeam(room, myTeams.get(room.getId())).getId())
                .collect(Collectors.toSet()));

        return visible.stream()
                .map(room -> toRoom(room, myTeams.get(room.getId()),
                        lastMessages.get(room.getId()), stats))
                .sorted(ROOM_ORDER)
                .toList();
    }

    private ChatRoomResponse toRoom(MatchRequest request, Team myTeam, ChatMessage lastMessage,
                                    Map<Long, ReviewStats> stats) {
        MatchPost post = request.getPost();
        Team other = otherTeam(request, myTeam);
        return new ChatRoomResponse(request.getId(), post.getId(), post.getTitle(),
                post.getMatchAt(), isOpen(request),
                TeamSummary.of(other, stats.getOrDefault(other.getId(), ReviewStats.EMPTY)),
                lastMessage == null ? null : ChatMessageResponse.of(lastMessage));
    }

    /**
     * 방마다 <b>내게 보이는</b> 마지막 메시지. 방 전체의 최대 id 가 내 워터마크 이하면
     * 내게 보이는 메시지가 하나도 없다는 뜻이라 null 이다 (나가기는 앞쪽만 자른다).
     */
    private Map<Long, ChatMessage> lastVisibleMessages(List<MatchRequest> rooms,
                                                       Map<Long, Team> myTeams,
                                                       Map<String, ChatLeave> leaves) {
        List<Long> requestIds = rooms.stream().map(MatchRequest::getId).toList();
        Map<Long, Long> lastIds = chatMessageRepository.findLastIdsOf(requestIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        Map<Long, Long> visibleIds = new HashMap<>();
        for (MatchRequest room : rooms) {
            Long lastId = lastIds.get(room.getId());
            long watermark = watermark(leaves, room, myTeams.get(room.getId()));
            if (lastId != null && lastId > watermark) {
                visibleIds.put(room.getId(), lastId);
            }
        }
        if (visibleIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new LinkedHashSet<>(visibleIds.values());
        Map<Long, ChatMessage> byId = chatMessageRepository.findAllWithSenderByIdIn(ids).stream()
                .collect(Collectors.toMap(ChatMessage::getId, Function.identity()));
        Map<Long, ChatMessage> result = new HashMap<>();
        visibleIds.forEach((requestId, messageId) -> {
            ChatMessage message = byId.get(messageId);
            if (message != null) {
                result.put(requestId, message);
            }
        });
        return result;
    }

    /** 내 팀들의 나가기 상태를 한 번에 긁어 (requestId,teamId) 로 찾을 수 있게 담는다. */
    private Map<String, ChatLeave> leavesOf(Collection<Team> teams) {
        Set<Long> teamIds = teams.stream().map(Team::getId).collect(Collectors.toSet());
        return chatLeaveRepository.findByTeam_IdIn(teamIds).stream()
                .collect(Collectors.toMap(
                        leave -> key(leave.getRequestId(), leave.getTeamId()),
                        Function.identity()));
    }

    private boolean isHidden(Map<String, ChatLeave> leaves, MatchRequest request, Team myTeam) {
        ChatLeave leave = leaves.get(key(request.getId(), myTeam.getId()));
        return leave != null && leave.isHidden();
    }

    private long watermark(Map<String, ChatLeave> leaves, MatchRequest request, Team myTeam) {
        ChatLeave leave = leaves.get(key(request.getId(), myTeam.getId()));
        return leave == null ? NO_WATERMARK : leave.getWatermark();
    }

    private long watermarkOf(Long requestId, Team myTeam) {
        return chatLeaveRepository.findByRequest_IdAndTeam_Id(requestId, myTeam.getId())
                .map(ChatLeave::getWatermark)
                .orElse(NO_WATERMARK);
    }

    private static String key(Long requestId, Long teamId) {
        return requestId + ":" + teamId;
    }

    /**
     * 당사자 팀의 주장인지 (계약서 §6-1). 아니면 403 이다 — 팀이 없는 사용자, 그리고 그
     * 팀의 ADMIN·MEMBER 도 여기서 걸린다.
     *
     * 권한을 상태보다 먼저 보는 게 핵심이다. 순서를 바꾸면 제3자가 409/200 의 차이만으로
     * "그 매칭이 수락됐는가"를 알아낼 수 있다.
     */
    private MatchRequest requireParticipant(Long requestId, User user) {
        MatchRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        teamOf(request, user);
        if (!request.isAccepted()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_ACCEPTED);
        }
        return request;
    }

    /** 이 사용자가 주장인 쪽 팀. 어느 쪽도 아니면 403. */
    private Team teamOf(MatchRequest request, User user) {
        MatchPost post = request.getPost();
        if (post.getTeam().isOwnedBy(user.getId())) {
            return post.getTeam();
        }
        if (request.getApplicantTeam().isOwnedBy(user.getId())) {
            return request.getApplicantTeam();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private Team otherTeam(MatchRequest request, Team myTeam) {
        MatchPost post = request.getPost();
        return post.getTeam().getId().equals(myTeam.getId())
                ? request.getApplicantTeam()
                : post.getTeam();
    }

    /** 서버 시계로만 판정한다 (계약서 §6-1). 기기 시계가 어긋나도 결과가 갈리면 안 된다. */
    private boolean isOpen(MatchRequest request) {
        return !request.getPost().hasPassed();
    }

    /**
     * 받는 사람은 상대 팀 주장이다. 보낸 사람에게 자기 메시지 알림이 갈 이유가 없다.
     *
     * v1.13.0: 상대가 방을 나갔으면 보내지 않는다. 나가기의 뜻이 "이 방에서 손 뗀다"인데
     * 알림만 계속 오면 나가기가 아무 일도 하지 않은 것처럼 보인다. SYSTEM 안내는 애초에
     * 이 경로를 타지 않아 발송이 없다.
     */
    private void publish(MatchRequest request, Team senderTeam, String content) {
        MatchPost post = request.getPost();
        Team opponent = otherTeam(request, senderTeam);
        boolean opponentLeft = chatLeaveRepository
                .findByRequest_IdAndTeam_Id(request.getId(), opponent.getId())
                .map(ChatLeave::isHidden)
                .orElse(false);
        if (opponentLeft) {
            return;
        }
        eventPublisher.publishEvent(new ChatPushEvent(opponent.getOwner().getId(),
                request.getId(), post.getId(), senderTeam.getName(), content));
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
