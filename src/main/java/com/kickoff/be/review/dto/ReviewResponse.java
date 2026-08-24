package com.kickoff.be.review.dto;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.review.entity.Review;
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

    public static ReviewResponse from(Review review) {
        MatchRequest request = review.getRequest();
        MatchPost post = request.getPost();
        return new ReviewResponse(
                review.getId(),
                request.getId(),
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                TeamSummary.from(review.getReviewerTeam()),
                review.getTargetTeam().getId(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
