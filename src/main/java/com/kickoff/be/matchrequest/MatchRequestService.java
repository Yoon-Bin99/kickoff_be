package com.kickoff.be.matchrequest;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.matchrequest.dto.RequestCreateRequest;
import com.kickoff.be.matchrequest.dto.RequestResponse;
import com.kickoff.be.post.MatchPost;
import com.kickoff.be.post.MatchPostRepository;
import com.kickoff.be.team.Team;
import com.kickoff.be.team.TeamRepository;
import com.kickoff.be.user.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
        return RequestResponse.of(saved, user.getId());
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
        return RequestResponse.of(request, user.getId());
    }

    @Transactional
    public RequestResponse reject(Long requestId, User user) {
        MatchRequest request = findRequest(requestId);
        requirePostAuthor(request, user);
        if (!request.isPending()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_PENDING);
        }
        request.reject();
        return RequestResponse.of(request, user.getId());
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
        return RequestResponse.of(request, user.getId());
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

    private MatchRequest findRequest(Long requestId) {
        return requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
    }

    private void requirePostAuthor(MatchRequest request, User user) {
        if (!request.getPost().isWrittenBy(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private List<RequestResponse> toResponses(List<MatchRequest> requests, User viewer) {
        Long viewerId = viewer == null ? null : viewer.getId();
        return requests.stream().map(r -> RequestResponse.of(r, viewerId)).toList();
    }
}
