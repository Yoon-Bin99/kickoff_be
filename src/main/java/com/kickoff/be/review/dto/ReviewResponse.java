package com.kickoff.be.review.dto;

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
        int rating,
        String comment,
        OffsetDateTime createdAt
) {

    /** reviewerTeamStats 는 호출자가 배치로 모아 넘긴다 (계약서 §2, v1.10.0). */
    public static ReviewResponse of(Review review, ReviewStats reviewerTeamStats) {
        MatchRequest request = review.getRequest();
        MatchPost post = request.getPost();
        return new ReviewResponse(
                review.getId(),
                request.getId(),
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                TeamSummary.of(review.getReviewerTeam(), reviewerTeamStats),
                review.getTargetTeam().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
