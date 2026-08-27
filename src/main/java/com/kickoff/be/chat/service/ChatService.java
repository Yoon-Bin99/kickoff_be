package com.kickoff.be.chat.service;

import com.kickoff.be.chat.dto.ChatMessageResponse;
import com.kickoff.be.chat.dto.ChatResponse;
import com.kickoff.be.chat.dto.ChatSendRequest;
import com.kickoff.be.chat.entity.ChatMessage;
import com.kickoff.be.chat.repository.ChatMessageRepository;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.push.dto.ChatPushEvent;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 채팅 (계약서 §6-1, v1.12.0).
 *
 * 방 리소스가 따로 없다 — 수락된 신청 하나가 곧 방이다. 참여자는 <b>두 팀의 주장뿐</b>이고
 * ADMIN·MEMBER 는 못 들어온다. 일정 조율 채널이라 1:1 로 못박은 사용자 결정이다.
 *
 * 검사 순서는 리뷰(§7)와 같이 <b>권한 → 상태</b>다. 제3자에게는 그 매칭이 수락됐는지조차
 * 알려줄 이유가 없다.
 *
 * 전송은 matchAt 까지만이고 그 뒤로는 읽기 전용으로 남는다 — 계좌·장소처럼 주고받은 정보를
 * 나중에 다시 볼 수 있어야 해서 지우지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final ChatMessageRepository chatMessageRepository;
    private final MatchRequestRepository requestRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 방 조회 (계약서 §6-1).
     *
     * {@code after} 가 없으면 <b>최신</b> limit 개를 오름차순으로 준다(첫 로드), 있으면 그
     * 이후만 준다(폴링). 어느 쪽이든 응답은 오래된 것부터다 — FE 가 그대로 이어 붙인다.
     */
    @Transactional(readOnly = true)
    public ChatResponse messages(Long requestId, User user, Long after, Integer limit) {
        MatchRequest request = requireParticipant(requestId, user);
        Pageable page = PageRequest.ofSize(clampLimit(limit));

        List<ChatMessage> messages;
        if (after == null) {
            // 최신부터 limit 개를 받아 뒤집는다. 오름차순으로 자르면 방의 가장 오래된
            // 대화만 나와서, 대화가 길어질수록 화면이 엉뚱해진다.
            messages = new ArrayList<>(
                    chatMessageRepository.findByRequest_IdOrderByIdDesc(requestId, page));
            Collections.reverse(messages);
        } else {
            messages = chatMessageRepository
                    .findByRequest_IdAndIdGreaterThanOrderByIdAsc(requestId, after, page);
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
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .request(matchRequest)
                .senderTeam(senderTeam)
                .content(request.content())
                .build());
        publish(matchRequest, senderTeam, request.content());
        return ChatMessageResponse.of(saved);
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

    /** 서버 시계로만 판정한다 (계약서 §6-1). 기기 시계가 어긋나도 결과가 갈리면 안 된다. */
    private boolean isOpen(MatchRequest request) {
        return !request.getPost().hasPassed();
    }

    /** 받는 사람은 상대 팀 주장이다. 보낸 사람에게 자기 메시지 알림이 갈 이유가 없다. */
    private void publish(MatchRequest request, Team senderTeam, String content) {
        MatchPost post = request.getPost();
        Team opponent = post.getTeam().getId().equals(senderTeam.getId())
                ? request.getApplicantTeam()
                : post.getTeam();
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
