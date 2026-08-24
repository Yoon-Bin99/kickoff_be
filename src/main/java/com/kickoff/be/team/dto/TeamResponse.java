package com.kickoff.be.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import java.time.OffsetDateTime;

public record TeamResponse(
        Long id,
        String name,
        String region,
        String homeGround,
        SkillLevel skillLevel,
        AgeGroup ageGroup,
        int memberCount,
        String introduction,
        String logoUrl,
        String ownerNickname,
        @JsonProperty("isMine") boolean isMine,
        OffsetDateTime createdAt,
        long reviewCount,
        Double averageRating
) {

    /**
     * viewerId 는 비로그인이면 null — 그때 isMine 은 false.
     * reviewStats 를 인자로 받는 이유: 이 DTO 는 팀 조회와 글 상세 양쪽에서 만들어지는데,
     * 집계를 안에서 조회하면 리포지터리 의존이 DTO 로 새고 호출자가 쿼리 수를 통제할 수 없다.
     */
    public static TeamResponse of(Team team, Long viewerId, ReviewStats reviewStats) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getRegion(),
                team.getHomeGround(),
                team.getSkillLevel(),
                team.getAgeGroup(),
                team.getMemberCount(),
                team.getIntroduction(),
                team.getLogoUrl(),
                team.getOwner().getNickname(),
                team.isOwnedBy(viewerId),
                team.getCreatedAt(),
                reviewStats.count(),
                reviewStats.average()
        );
    }
}
