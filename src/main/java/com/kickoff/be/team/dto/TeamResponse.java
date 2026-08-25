package com.kickoff.be.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRole;
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
        /**
         * 소유자 여부. v1.9.0 부터 {@code myRole == OWNER} 와 동치이고, 구버전 FE 를 위해
         * 남겨 둔다 (계약서 §4-2 하위호환).
         */
        @JsonProperty("isMine") boolean isMine,
        /** OWNER · ADMIN · null (비로그인이거나 무관계) — 계약서 §4-2, v1.9.0. */
        TeamRole myRole,
        OffsetDateTime createdAt,
        long reviewCount,
        Double averageRating,

        // ── 팀 프로필 확장 (계약서 §4-1, v1.8.0)
        Integer foundedYear,
        String teamColor,
        String formation
) {

    /**
     * viewerId 는 비로그인이면 null — 그때 isMine 은 false.
     * reviewStats 를 인자로 받는 이유: 이 DTO 는 팀 조회와 글 상세 양쪽에서 만들어지는데,
     * 집계를 안에서 조회하면 리포지터리 의존이 DTO 로 새고 호출자가 쿼리 수를 통제할 수 없다.
     */
    public static TeamResponse of(Team team, Long viewerId, ReviewStats reviewStats) {
        return of(team, viewerId, reviewStats, team.isOwnedBy(viewerId) ? TeamRole.OWNER : null);
    }

    /**
     * 역할을 아는 호출자가 쓰는 형태 (계약서 §4-2). 관리자 판정에는 조회가 한 번 더
     * 필요해서, 그 비용을 치를지는 호출자가 정한다 — 위 형태는 소유자만 가려내고 관리자는
     * null 로 둔다.
     */
    public static TeamResponse of(Team team, Long viewerId, ReviewStats reviewStats,
                                  TeamRole myRole) {
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
                myRole,
                team.getCreatedAt(),
                reviewStats.count(),
                reviewStats.average(),
                team.getFoundedYear(),
                team.getTeamColor(),
                team.getFormation()
        );
    }
}
