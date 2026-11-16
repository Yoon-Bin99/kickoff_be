package com.kickoff.be.review.dto;

import com.kickoff.be.common.ModerationResponse;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.dto.TeamSummary;
import java.time.OffsetDateTime;

/** 계약서 §7 의 ReviewResponse. */
public record ReviewResponse(
        Long id,
        Long requestId,
        Long postId,
        String postTitle,
        OffsetDateTime matchAt,
        TeamSummary reviewerTeam,
        Long targetTeamId,
        /** 가려진 리뷰는 null (계약서 §7-2) — 자리 표시만 남는다. */
        Integer rating,
        String comment,
        /** 운영 조치 (계약서 §7-2, v1.35.0). 조치가 없으면 null. */
        ModerationResponse moderation,
        OffsetDateTime createdAt
) {

    /**
     * reviewerTeamStats 는 호출자가 배치로 모아 넘긴다 (계약서 §2, v1.10.0).
     *
     * <b>가려진 리뷰는 목록에서 빼지 않고 별점·내용만 비운다</b> (계약서 §7-2) —
     * 조치가 있었다는 사실을 그 자리에 남겨야 한다. FE 는 {@code moderation} 이 있으면
     * 표시 문구를 그린다.
     */
    public static ReviewResponse of(Review review, ReviewStats reviewerTeamStats) {
        MatchRequest request = review.getRequest();
        MatchPost post = request.getPost();
        ModerationResponse moderation = ModerationResponse.of(review.getModeration());
        return new ReviewResponse(
                review.getId(),
                request.getId(),
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                TeamSummary.of(review.getReviewerTeam(), reviewerTeamStats),
                review.getTargetTeam().getId(),
                moderation == null ? review.getRating() : null,
                moderation == null ? review.getComment() : null,
                moderation,
                review.getCreatedAt()
        );
    }
}
