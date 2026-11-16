package com.kickoff.be.block.service;

import com.kickoff.be.block.dto.BlockCreateRequest;
import com.kickoff.be.block.dto.BlockResponse;
import com.kickoff.be.block.entity.TeamBlock;
import com.kickoff.be.block.repository.TeamBlockRepository;
import com.kickoff.be.chatlink.service.ChatOutboxRecorder;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.push.dto.MatchPushEvent;
import com.kickoff.be.push.dto.PushEventType;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.team.service.TeamAuthz;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 차단 (계약서 §7-2 「차단」, v1.35.0).
 *
 * <b>차단은 두 겹이다.</b> 바깥 겹은 그 사람의 화면 — 차단한 팀의 글·리뷰가 보이지 않는다.
 * 안쪽 겹은 두 팀 사이 — 신청도 채팅 전송도 막힌다. 안쪽은 <b>차단한 사람이 감독일 때만</b>
 * 생긴다. 팀원이 상대 팀을 차단했다고 감독끼리 잡은 경기가 깨지면 안 된다.
 *
 * <b>예정된 경기는 조용히 깨지 않는다.</b> 매칭이 잡혀 있는데 차단하면 409 로 되묻고,
 * 확인을 받은 뒤에야 §6-2 와 똑같이 취소한다 — 조율 수단(연락처·채팅)만 끊고 경기는
 * 남겨 두면 양 팀이 구장에서 만나게 된다.
 */
@Service
@RequiredArgsConstructor
public class BlockService {

    private final TeamBlockRepository blockRepository;
    private final TeamRepository teamRepository;
    private final TeamAuthz teamAuthz;
    private final MatchRequestRepository requestRepository;
    private final ReviewRepository reviewRepository;
    private final ChatOutboxRecorder chatOutboxRecorder;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 차단한다. 이미 차단한 팀이면 같은 응답을 그대로 돌려준다 (멱등 — 계약서 §7-2).
     *
     * @return 새로 차단했으면 {@code true} (컨트롤러가 201 과 200 을 가른다)
     */
    @Transactional
    public Created block(User user, BlockCreateRequest request) {
        Team target = teamRepository.findWithOwnerById(request.teamId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND));
        requireNotMyTeam(target, user);

        TeamBlock existing = blockRepository
                .findByBlockerIdAndBlockedTeamId(user.getId(), target.getId())
                .orElse(null);
        if (existing != null) {
            return new Created(false, toResponse(existing));
        }

        Team myTeam = teamRepository.findByOwnerId(user.getId()).orElse(null);
        if (myTeam != null) {
            handleUpcomingMatches(myTeam, target, request.cancels(), user);
            clearPendingRequests(myTeam, target);
        }

        TeamBlock saved = blockRepository.save(TeamBlock.builder()
                .blocker(user)
                .blockedTeam(target)
                .build());
        notifyChat(myTeam, target);
        return new Created(true, toResponse(saved));
    }

    /** 차단한 적이 없어도 204 다 (계약서 §7-2) — 해제의 결과는 "차단이 없다"로 같다. */
    @Transactional
    public void unblock(User user, Long teamId) {
        blockRepository.findByBlockerIdAndBlockedTeamId(user.getId(), teamId)
                .ifPresent(block -> {
                    blockRepository.delete(block);
                    teamRepository.findByOwnerId(user.getId()).ifPresent(myTeam ->
                            notifyChat(myTeam, block.getBlockedTeam()));
                });
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> list(User user) {
        List<TeamBlock> blocks = blockRepository
                .findByBlockerIdOrderByCreatedAtDesc(user.getId());
        Map<Long, ReviewStats> stats = reviewRepository.statsMapOf(
                blocks.stream().map(block -> block.getBlockedTeam().getId()).toList());
        return blocks.stream()
                .map(block -> BlockResponse.of(block,
                        stats.getOrDefault(block.getBlockedTeam().getId(), ReviewStats.EMPTY)))
                .toList();
    }

    // ── 예정된 매칭과 대기 중 신청

    /**
     * 두 팀 사이의 예정된 매칭을 본다 (계약서 §7-2).
     *
     * 확인 없이 차단하면 409 {@code BLOCK_HAS_UPCOMING_MATCH} 이고, 그 응답에 가장 가까운
     * 경기 시각을 실어 보낸다 — FE 확인창이 날짜를 말할 수 있어야 한다(v1.35.2).
     */
    private void handleUpcomingMatches(Team myTeam, Team target, boolean cancel, User user) {
        List<MatchRequest> upcoming = upcomingBetween(myTeam, target);
        if (upcoming.isEmpty()) {
            return;
        }
        if (!cancel) {
            throw new BlockHasUpcomingMatchException(upcoming.stream()
                    .map(request -> request.getPost().getMatchAt())
                    .min(Comparator.naturalOrder())
                    .orElseThrow());
        }
        for (MatchRequest request : upcoming) {
            // §6-2 와 똑같이 취소한다 — 상대 팀에 MATCH_CANCELED 푸시, 글은 OPEN 복구.
            request.cancelMatch();
            request.getPost().reopen();
            chatOutboxRecorder.roomInvalidated(List.of(request.getId()));
            publish(PushEventType.MATCH_CANCELED, counterpartOwnerId(request, user), request);
        }
    }

    /**
     * 대기 중인 신청은 정리한다 (계약서 §7-2).
     *
     * <b>방향에 따라 다른 상태가 된다.</b> 내 팀이 보낸 것은 내가 거두는 것이니
     * {@code CANCELED}, 내 글에 온 것은 내가 거절하는 것이니 {@code REJECTED} 다 —
     * 거절 푸시도 그대로 나간다. 차단 사실은 어느 쪽 문구에도 드러나지 않는다.
     */
    private void clearPendingRequests(Team myTeam, Team target) {
        for (MatchRequest request : requestRepository.findSentByTeamId(myTeam.getId())) {
            if (request.isPending() && request.getPost().getTeam().getId()
                    .equals(target.getId())) {
                request.cancel();
            }
        }
        for (MatchRequest request : requestRepository.findReceivedByTeamId(myTeam.getId())) {
            if (request.isPending() && request.getApplicantTeam().getId()
                    .equals(target.getId())) {
                request.reject();
                publish(PushEventType.REQUEST_REJECTED, target.getOwner().getId(), request);
            }
        }
    }

    private List<MatchRequest> upcomingBetween(Team myTeam, Team target) {
        OffsetDateTime now = OffsetDateTime.now();
        return requestRepository.findAcceptedBetween(myTeam.getId(), target.getId()).stream()
                .filter(request -> request.getPost().getMatchAt().isAfter(now))
                .toList();
    }

    /**
     * 알림 이벤트를 발행한다. 실제 발송은 AFTER_COMMIT 리스너가 맡는다 — 여기서 바로
     * 보내면 롤백된 트랜잭션에 대한 유령 알림이 나간다 (계약서 §8).
     *
     * 엔티티가 아니라 값만 실어 보낸다. 커밋 이후에는 지연 로딩을 걸 세션이 없다.
     */
    private void publish(PushEventType type, Long recipientUserId, MatchRequest request) {
        eventPublisher.publishEvent(new MatchPushEvent(type, recipientUserId,
                request.getId(), request.getPost().getId(), request.getPost().getTitle(),
                request.getApplicantTeam().getName()));
    }

    private Long counterpartOwnerId(MatchRequest request, User user) {
        return request.getPost().isWrittenBy(user.getId())
                ? request.getApplicantTeam().getOwner().getId()
                : request.getPost().getTeam().getOwner().getId();
    }

    /**
     * 차단·해제로 두 팀 사이의 수락된 방 상태가 달라진다 (계약서 §7-2·§10).
     *
     * 알리지 않으면 상대 화면의 입력창이 최대 15초 동안 열려 있다({@code RoomContext}
     * 캐시 수명) — 보낼 수 없는 방에 메시지를 쓰는 화면이다. 채팅 서버는 이 이벤트를
     * 받고도 사유를 드러내지 않는다(§10 — {@code reason: "SCHEDULE_CHANGED"}).
     */
    private void notifyChat(Team myTeam, Team target) {
        if (myTeam == null) {
            return;
        }
        List<Long> requestIds = requestRepository
                .findAcceptedBetween(myTeam.getId(), target.getId()).stream()
                .map(MatchRequest::getId)
                .toList();
        if (!requestIds.isEmpty()) {
            chatOutboxRecorder.roomInvalidated(requestIds);
        }
    }

    private void requireNotMyTeam(Team target, User user) {
        if (teamAuthz.isMemberOfAnyKind(target, user.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    List.of(new ErrorResponse.FieldError("teamId",
                            "내가 속한 팀은 차단할 수 없습니다.")));
        }
    }

    private BlockResponse toResponse(TeamBlock block) {
        return BlockResponse.of(block,
                reviewRepository.statsOf(block.getBlockedTeam().getId()));
    }

    /** 새로 차단했는지(201)와 이미 차단돼 있었는지(200)를 가른다. */
    public record Created(boolean fresh, BlockResponse response) {
    }
}
