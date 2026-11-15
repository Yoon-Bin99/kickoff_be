package com.kickoff.be.chatlink.service;

import com.kickoff.be.chatlink.dto.ChatPushRequest;
import com.kickoff.be.chatlink.dto.RoomContext;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.push.dto.ChatPushEvent;
import com.kickoff.be.review.dto.ReviewStats;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kickoff.be.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 서버가 묻는 것에 답한다 (계약서 §10). FE 는 이 경로를 부르지 않는다.
 *
 * <b>여기서 권한을 보지 않는다.</b> 부르는 쪽이 사용자가 아니라 채팅 서버라서다 — 호출
 * 자체의 자격은 공유 비밀 헤더가 보증하고({@code InternalSecretFilter}), "이 사용자가 이
 * 방에 들어갈 수 있는가"는 §6-1 의 규칙대로 채팅 서버가 판정한다. 양쪽에서 판정하면
 * 규칙이 두 곳에 생기고 한쪽만 고쳐지는 날이 온다.
 */
@Service
@RequiredArgsConstructor
public class InternalChatService {

    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 방 하나 (계약서 §10). <b>신청 상태와 무관하게</b> 돌려준다 — 취소된 방도, 아직
     * 수락되지 않은 신청도 여기서는 404 가 아니다.
     */
    @Transactional(readOnly = true)
    public RoomContext room(Long requestId) {
        MatchRequest request = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        return RoomContext.of(request, statsOf(List.of(request)));
    }

    /**
     * 그 사용자의 방 목록 (계약서 §10). 범위는 §6-1 의 채팅 탭과 같다 — 그가 OWNER 인 팀이
     * 당사자인 ACCEPTED 신청 전부.
     *
     * <b>없는 사용자도 빈 배열이다.</b> 404 로 가르면 탈퇴 직후 토큰이 남아 있는 정상
     * 상황에서 채팅 서버가 오류를 띄운다. 사용자 조회를 아예 하지 않아 왕복도 하나 준다.
     *
     * 쿼리는 방 개수와 무관하게 두 번이다 — 방 목록 하나, 양쪽 팀 평점 하나. 방마다 평점을
     * 물으면 목록이 길어질수록 쿼리가 는다({@code ChatService.myChats} 와 같은 이유).
     */
    @Transactional(readOnly = true)
    public List<RoomContext> rooms(Long userId) {
        List<MatchRequest> rooms = requestRepository.findAcceptedRoomsOf(userId);
        if (rooms.isEmpty()) {
            return List.of();
        }
        Map<Long, ReviewStats> stats = statsOf(rooms);
        return rooms.stream().map(room -> RoomContext.of(room, stats)).toList();
    }

    /**
     * 푸시 요청 (계약서 §10). 받자마자 202 로 답하고 발송은 비동기다.
     *
     * <b>기존 채팅 푸시 경로를 그대로 탄다</b> — 같은 이벤트, 같은 리스너, 같은 payload.
     * 따로 만들면 §6-1 의 딥링크 키나 본문 50자 규칙이 두 벌이 되어 한쪽만 틀어진다.
     *
     * 쓰기가 없는데 {@code @Transactional} 인 이유가 있다. {@code ChatPushListener} 는
     * {@code AFTER_COMMIT} 리스너라 <b>트랜잭션이 없으면 아예 불리지 않는다</b> — 그러면
     * 202 만 돌려주고 알림은 조용히 사라진다. 빈 트랜잭션이라도 커밋이 있어야 그 경로가
     * 돈다. 발송 실패를 삼키는 것도 리스너 쪽 규칙 그대로다(§8).
     */
    @Transactional
    public void push(ChatPushRequest request) {
        eventPublisher.publishEvent(new ChatPushEvent(request.recipientUserId(),
                request.requestId(), request.postId(), request.senderTeamName(),
                request.content()));
    }

    private Map<Long, ReviewStats> statsOf(List<MatchRequest> rooms) {
        Set<Long> teamIds = new LinkedHashSet<>();
        for (MatchRequest room : rooms) {
            teamIds.add(room.getPost().getTeam().getId());
            teamIds.add(room.getApplicantTeam().getId());
        }
        return reviewRepository.statsMapOf(teamIds);
    }
}
