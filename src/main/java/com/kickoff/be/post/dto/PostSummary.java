package com.kickoff.be.post.dto;

import com.kickoff.be.common.ModerationResponse;
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
        /** 경기장 주소 (계약서 §5, v1.29.0). 직접 입력한 글·옛 글은 null. */
        String locationAddress,
        String region,
        SkillLevel preferredSkillLevel,
        Integer rentalFee,
        Integer depositAmount,
        PostStatus status,
        /**
         * 운영 조치 (계약서 §7-2, v1.35.0). 조치가 없으면 null.
         *
         * 가려진 글은 목록에서 빠지므로 이 칸이 채워져 나가는 곳은 <b>내 글 목록</b>뿐이다
         * (작성자는 자기 글이 왜 안 보이는지 알아야 한다).
         */
        ModerationResponse moderation,
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
                post.getLocationAddress(),
                post.getRegion(),
                post.getPreferredSkillLevel(),
                post.getRentalFee(),
                post.getDepositAmount(),
                post.getStatus(),
                ModerationResponse.of(post.getModeration()),
                requestCount,
                TeamSummary.of(post.getTeam(), teamStats),
                post.getCreatedAt()
        );
    }
}
