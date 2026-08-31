package com.kickoff.be.matchrequest.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.matchrequest.dto.RequestCreateRequest;
import com.kickoff.be.matchrequest.dto.RequestResponse;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.repository.MatchPostRepository;
import com.kickoff.be.push.dto.MatchPushEvent;
import com.kickoff.be.push.dto.PushEventType;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.repository.TeamRecordRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchRequestService {

    /** 살아 있는 신청 — 이 상태로 이미 걸려 있으면 중복 신청이다. */
    private static final List<RequestStatus> ACTIVE =
            List.of(RequestStatus.PENDING, RequestStatus.ACCEPTED);

    private final MatchRequestRepository requestRepository;
    private final MatchPostRepository postRepository;
    private final TeamRepository teamRepository;
    private final ReviewRepository reviewRepository;
    private final TeamRecordRepository teamRecordRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public RequestResponse create(Long postId, User user, RequestCreateRequest request) {
        Team myTeam = teamRepository.findWithOwnerByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_REQUIRED));
        MatchPost post = postRepository.findWithTeamAndOwnerById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (post.getTeam().getId().equals(myTeam.getId())) {
            throw new BusinessException(ErrorCode.SELF_REQUEST_NOT_ALLOWED);
        }
        // 지난 경기는 status 가 OPEN 이어도 신청을 받지 않는다 (계약서 §5 지난 경기 규칙).
        if (!post.acceptsRequests()) {
            throw new BusinessException(ErrorCode.POST_NOT_OPEN);
        }
        // 취소·거절 이력은 재신청을 막지 않는다 (계약서 §6).
        if (requestRepository.existsByPostIdAndApplicantTeamIdAndStatusIn(
                postId, myTeam.getId(), ACTIVE)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST);
        }

        MatchRequest saved = requestRepository.save(MatchRequest.builder()
                .post(post)
                .applicantTeam(myTeam)
                .message(request == null ? null : request.message())
                .build());
        publish(PushEventType.REQUEST_RECEIVED, post.getTeam().getOwner().getId(), saved);
        // 방금 만든 신청이라 리뷰도 전적도 있을 수 없다. 팀 평점은 두 팀뿐이라 그대로 조회한다.
        return RequestResponse.of(saved, user.getId(), false, false,
                reviewRepository.statsOf(myTeam.getId()),
                reviewRepository.statsOf(post.getTeam().getId()));
    }

    /** 한 글에 온 신청 — 글 작성자만 볼 수 있다. */
    @Transactional(readOnly = true)
    public List<RequestResponse> getByPost(Long postId, User user) {
        MatchPost post = postRepository.findWithTeamAndOwnerById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!post.isWrittenBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toResponses(requestRepository.findByPostIdOrdered(postId), user);
    }

    /**
     * 내 팀이 쓴 모든 글에 온 신청을 한 번에 (계약서 §6).
     * 팀이 없으면 받은 신청도 없다 — 조회성 API 라 에러가 아니라 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> getReceived(User user) {
        Team myTeam = teamRepository.findByOwnerId(user.getId()).orElse(null);
        if (myTeam == null) {
            return List.of();
        }
        return toResponses(requestRepository.findReceivedByTeamId(myTeam.getId()), user);
    }

    @Transactional(readOnly = true)
    public List<RequestResponse> getSent(User user) {
        Team myTeam = teamRepository.findByOwnerId(user.getId()).orElse(null);
        if (myTeam == null) {
            return List.of();
        }
        return toResponses(requestRepository.findSentByTeamId(myTeam.getId()), user);
    }

    /**
     * 수락하면 글이 MATCHED 로 바뀌고 같은 글의 나머지 PENDING 은 전부 자동 거절된다.
     * 이 시점부터 양쪽에 연락처가, 신청 팀에게 계좌가 공개된다.
     */
    @Transactional
    public RequestResponse accept(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        requirePostAuthor(request, user);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_PENDING);
        }

        // 수락으로 상태를 바꾸기 전에 긁어와야 자기 자신이 섞이지 않는다.
        List<MatchRequest> others = requestRepository.findByPostIdAndStatusAndIdNot(
                request.getPost().getId(), RequestStatus.PENDING, requestId);
        others.forEach(MatchRequest::reject);

        request.accept();
        request.getPost().markMatched();

        publish(PushEventType.REQUEST_ACCEPTED, applicantOwnerId(request), request);
        // 자동 거절된 팀들도 결과를 알아야 한다 (계약서 §8).
        others.forEach(other ->
                publish(PushEventType.REQUEST_REJECTED, applicantOwnerId(other), other));
        return toResponse(request, user);
    }

    @Transactional
    public RequestResponse reject(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        requirePostAuthor(request, user);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_PENDING);
        }
        request.reject();
        publish(PushEventType.REQUEST_REJECTED, applicantOwnerId(request), request);
        return toResponse(request, user);
    }

    /** 상대 팀 입금 확인. 기록용 플래그라 글/신청 상태는 그대로 둔다 (계약서 §6). */
    @Transactional
    public RequestResponse confirmDeposit(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        requirePostAuthor(request, user);
        if (!request.isAccepted()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_ACCEPTED);
        }
        request.confirmDeposit();
        publish(PushEventType.DEPOSIT_CONFIRMED, applicantOwnerId(request), request);
        return toResponse(request, user);
    }

    /**
     * 수락된 매칭을 취소한다 — <b>양 팀 어느 쪽이든</b> (계약서 §6-2, v1.20.0).
     *
     * 검사 순서가 규정돼 있다: <b>권한 먼저, 상태는 그다음</b> (계약서 §7). 뒤집으면
     * 제3자가 409/200 의 차이로 "그 매칭이 수락된 상태인가"를 알아낼 수 있다.
     *
     * 취소가 닫는 것들(연락처·payment·채팅·입금확인·리뷰·전적)은 <b>여기서 따로 닫지
     * 않는다.</b> 전부 {@link MatchRequest#isAccepted()} 를 통과하게 돼 있어서, 상태가
     * ACCEPTED 가 아니게 되는 순간 함께 닫힌다. 각각을 여기서 또 닫으면 규칙이 두 곳에
     * 생기고 한쪽만 고쳐지는 날이 온다.
     */
    @Transactional
    public RequestResponse cancelMatch(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        if (!isPartyOwner(request, user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!request.isAccepted()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_ACCEPTED);
        }
        // 경기가 이미 지났으면 취소가 아니라 리뷰·전적의 영역이다 (계약서 §6-2).
        if (request.getPost().hasPassed()) {
            throw new BusinessException(ErrorCode.MATCH_CANCEL_EXPIRED);
        }

        request.cancelMatch();
        request.getPost().reopen();

        publish(PushEventType.MATCH_CANCELED, counterpartOwnerId(request, user), request);
        return toResponse(request, user);
    }

    /** 신청 취소 — 신청한 팀만. */
    @Transactional
    public void cancel(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        if (!request.getApplicantTeam().isOwnedBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_PENDING);
        }
        request.cancel();
    }

    /**
     * 알림 이벤트를 발행한다. 실제 발송은 AFTER_COMMIT 리스너가 맡는다 — 여기서 바로 보내면
     * 롤백된 트랜잭션에 대한 유령 알림이 나간다 (계약서 §8).
     *
     * 엔티티가 아니라 값만 실어 보낸다. 커밋 이후에는 지연 로딩을 걸 세션이 없다.
     */
    private void publish(PushEventType type, Long recipientUserId, MatchRequest request) {
        MatchPost post = request.getPost();
        eventPublisher.publishEvent(new MatchPushEvent(type, recipientUserId,
                request.getId(), post.getId(), post.getTitle(),
                request.getApplicantTeam().getName()));
    }

    /** 알림 수신자는 언제나 팀의 소유자다 (계약서 §8). */
    private Long applicantOwnerId(MatchRequest request) {
        return request.getApplicantTeam().getOwner().getId();
    }

    private Long postOwnerId(MatchRequest request) {
        return request.getPost().getTeam().getOwner().getId();
    }

    /** 매칭 당사자 두 팀의 소유자인지 — 매칭 취소는 양쪽 다 할 수 있다 (계약서 §6-2). */
    private boolean isPartyOwner(MatchRequest request, User user) {
        return request.getPost().isWrittenBy(user.getId())
                || request.getApplicantTeam().isOwnedBy(user.getId());
    }

    /**
     * 취소 알림을 받을 쪽 — <b>취소한 사람의 반대편</b> (계약서 §6-2).
     *
     * 한쪽으로 굳혀 두면 그쪽이 취소했을 때 자기 자신에게 알림이 간다. 화면은 멀쩡하고
     * 상대는 매칭이 깨진 걸 모른 채 경기장에 나간다 — 에러가 없어서 드러나지도 않는다.
     */
    private Long counterpartOwnerId(MatchRequest request, User canceler) {
        return request.getPost().isWrittenBy(canceler.getId())
                ? applicantOwnerId(request)
                : postOwnerId(request);
    }

    private MatchRequest findRequest(Long requestId) {
        return requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
    }

    private void requirePostAuthor(MatchRequest request, User user) {
        if (!request.getPost().isWrittenBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private RequestResponse toResponse(MatchRequest request, User viewer) {
        Long viewerTeamId = viewerTeamId(viewer);
        boolean reviewed = viewerTeamId != null && reviewRepository
                .existsByRequestIdAndReviewerTeamId(request.getId(), viewerTeamId);
        boolean recorded = viewerTeamId != null && teamRecordRepository
                .existsByRequest_IdAndTeamId(request.getId(), viewerTeamId);
        Map<Long, ReviewStats> stats = teamStatsOf(List.of(request));
        return RequestResponse.of(request, viewer.getId(), reviewed, recorded,
                statOf(stats, request.getApplicantTeam().getId()),
                statOf(stats, request.getPost().getTeam().getId()));
    }

    private List<RequestResponse> toResponses(List<MatchRequest> requests, User viewer) {
        Long viewerId = viewer == null ? null : viewer.getId();
        Long viewerTeamId = viewerTeamId(viewer);
        Set<Long> reviewed = reviewedRequestIds(viewerTeamId, requests);
        Set<Long> recorded = recordedRequestIds(viewerTeamId, requests);
        Map<Long, ReviewStats> stats = teamStatsOf(requests);
        return requests.stream()
                .map(r -> RequestResponse.of(r, viewerId, reviewed.contains(r.getId()),
                        recorded.contains(r.getId()),
                        statOf(stats, r.getApplicantTeam().getId()),
                        statOf(stats, r.getPost().getTeam().getId())))
                .toList();
    }

    private Long viewerTeamId(User viewer) {
        if (viewer == null) {
            return null;
        }
        return teamRepository.findByOwnerId(viewer.getId()).map(Team::getId).orElse(null);
    }

    /** 목록의 myReviewWritten — 신청마다 exists 를 날리면 N+1 이라 한 번에 긁어온다. */
    private Set<Long> reviewedRequestIds(Long viewerTeamId, List<MatchRequest> requests) {
        if (viewerTeamId == null || requests.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(reviewRepository.findReviewedRequestIds(viewerTeamId,
                requests.stream().map(MatchRequest::getId).toList()));
    }

    /**
     * 목록에 실린 <b>양 팀</b>의 평점을 한 번에 (계약서 §2, v1.10.0).
     * 신청 하나에 팀이 둘이라, 배치가 없으면 페이지 크기의 두 배만큼 쿼리가 난다.
     */
    private Map<Long, ReviewStats> teamStatsOf(List<MatchRequest> requests) {
        return reviewRepository.statsMapOf(requests.stream()
                .flatMap(r -> Stream.of(r.getApplicantTeam().getId(),
                        r.getPost().getTeam().getId()))
                .distinct()
                .toList());
    }

    private static ReviewStats statOf(Map<Long, ReviewStats> stats, Long teamId) {
        return stats.getOrDefault(teamId, ReviewStats.EMPTY);
    }

    /** 목록의 myRecordWritten — 위와 같은 이유로 한 번에 긁어온다 (계약서 §4-1, v1.10.0). */
    private Set<Long> recordedRequestIds(Long viewerTeamId, List<MatchRequest> requests) {
        if (viewerTeamId == null || requests.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(teamRecordRepository.findRecordedRequestIds(viewerTeamId,
                requests.stream().map(MatchRequest::getId).toList()));
    }
}
