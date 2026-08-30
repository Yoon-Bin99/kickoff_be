package com.kickoff.be.post.dto;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.dto.TeamSummary;
import com.kickoff.be.team.entity.SkillLevel;
import java.time.OffsetDateTime;

/**
 * 목록 카드. 계좌 정보(bankName/accountNumber/accountHolder)는 절대 여기 들어가지 않는다.
 */
public record PostSummary(
        Long id,
        String title,
        OffsetDateTime matchAt,
        String location,
        String region,
        SkillLevel preferredSkillLevel,
        Integer rentalFee,
        Integer depositAmount,
        Double latitude,
        Double longitude,
        PostStatus status,
        long requestCount,
        TeamSummary team,
        OffsetDateTime createdAt
) {

    /** teamStats 는 호출자가 배치로 모아 넘긴다 — 카드마다 조회하면 N+1 이다 (계약서 §2). */
    public static PostSummary of(MatchPost post, long requestCount, ReviewStats teamStats) {
        return new PostSummary(
                post.getId(),
                post.getTitle(),
                post.getMatchAt(),
                post.getLocation(),
                post.getRegion(),
                post.getPreferredSkillLevel(),
                post.getRentalFee(),
                post.getDepositAmount(),
                post.getLatitude(),
                post.getLongitude(),
                post.getStatus(),
                requestCount,
                TeamSummary.of(post.getTeam(), teamStats),
                post.getCreatedAt()
        );
    }
}
