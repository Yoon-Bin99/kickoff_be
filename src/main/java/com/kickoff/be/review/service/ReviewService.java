package com.kickoff.be.review.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.repository.MatchRequestRepository;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.review.dto.ReviewCreateRequest;
import com.kickoff.be.review.dto.ReviewResponse;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.review.repository.ReviewRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository reviewRepository;
    private final MatchRequestRepository requestRepository;
    private final TeamRepository teamRepository;

    /**
     * 매칭 당사자가 상대 팀을 평가한다 (계약서 §7).
     *
     * 검사 순서는 <b>권한 → 상태</b>다. 제3자에게는 그 매칭이 수락됐는지, 경기가 끝났는지조차
     * 알려줄 이유가 없어서 403 을 먼저 낸다. 계약서는 조건을 나열만 하고 순서를 규정하지 않는다.
     */
    @Transactional
    public ReviewResponse create(Long requestId, User user, ReviewCreateRequest request) {
        MatchRequest matchRequest = requestRepository.findDetailById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));

        // 팀이 없으면 애초에 어느 매칭의 당사자도 될 수 없다.
        Team myTeam = teamRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        Team targetTeam = counterpartOf(matchRequest, myTeam);

        if (!matchRequest.isAccepted()) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AVAILABLE);
        }
        // 경기가 끝나야 평가할 게 생긴다.
        if (!matchRequest.getPost().hasPassed()) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AVAILABLE);
        }
        if (reviewRepository.existsByRequestIdAndReviewerTeamId(requestId, myTeam.getId())) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review saved = reviewRepository.save(Review.builder()
                .request(matchRequest)
                .reviewerTeam(myTeam)
                .targetTeam(targetTeam)
                .rating(request.rating())
                .comment(request.comment())
                .build());
        return ReviewResponse.from(saved);
    }

    /** 팀이 받은 리뷰 목록. 인증 불필요 — 누구나 팀 평판을 볼 수 있다 (계약서 §7). */
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getByTeam(Long teamId, int page, int size) {
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(reviewRepository.findByTargetTeamId(teamId, pageable),
                ReviewResponse::from);
    }

    /**
     * 평가 대상은 요청 본문이 아니라 매칭 관계에서 서버가 정한다.
     * 글 작성 팀이 쓰면 신청 팀을, 신청 팀이 쓰면 글 작성 팀을 평가한다.
     */
    private Team counterpartOf(MatchRequest request, Team myTeam) {
        MatchPost post = request.getPost();
        if (post.getTeam().getId().equals(myTeam.getId())) {
            return request.getApplicantTeam();
        }
        if (request.getApplicantTeam().getId().equals(myTeam.getId())) {
            return post.getTeam();
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
