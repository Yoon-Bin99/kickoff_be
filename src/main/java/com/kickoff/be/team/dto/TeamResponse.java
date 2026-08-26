package com.kickoff.be.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.JoinStatus;
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
        /** OWNER · ADMIN · MEMBER · null (비로그인이거나 무관계) — 계약서 §4-2·§4-3. */
        TeamRole myRole,
        /**
         * 내 가입 신청이 대기 중이면 PENDING, 아니면 null (계약서 §4-3, v1.11.0).
         *
         * 신청자 본인은 join-requests 목록을 볼 수 없어서(소유자·관리자 전용) 팀 페이지에서
         * 자기 신청 상태를 알 길이 없었다. 거절·취소 이력을 구분하지 않는 건 재신청이
         * 허용되기 때문이다 — 어느 쪽이든 FE 는 신청 버튼을 보여주면 된다.
         */
        JoinStatus myJoinStatus,
        OffsetDateTime createdAt,
        long reviewCount,
        Double averageRating,

        // ── 팀 프로필 확장 (계약서 §4-1, v1.8.0)
        Integer foundedYear,
        String teamColor,
        String formation,
        /** 수동 입력 기록에서 집계한 전적. 기록이 0건이면 전부 0 이다 (계약서 §4-1). */
        RecordSummary recordSummary
) {

    /**
     * viewerId 는 비로그인이면 null — 그때 isMine 은 false.
     * reviewStats 를 인자로 받는 이유: 이 DTO 는 팀 조회와 글 상세 양쪽에서 만들어지는데,
     * 집계를 안에서 조회하면 리포지터리 의존이 DTO 로 새고 호출자가 쿼리 수를 통제할 수 없다.
     */
    /**
     * 역할과 전적 요약은 <b>호출자가 넘긴다</b>. 둘 다 조회가 필요해서 DTO 안에서 구하면
     * 리포지터리 의존이 새고 호출자가 쿼리 수를 통제할 수 없다 (reviewStats 와 같은 이유).
     *
     * 기본값을 채워 주는 편의 생성자를 두지 않은 건 의도다. 그런 게 있으면 다음 사람이
     * 무심코 써서 기록이 있는 팀에 <b>0승 0무 0패</b>가 나가는데, 에러가 아니라 조용히
     * 틀린 값이라 화면을 보기 전에는 아무도 모른다.
     */
    public static TeamResponse of(Team team, Long viewerId, ReviewStats reviewStats,
                                  TeamRole myRole, RecordSummary recordSummary,
                                  JoinStatus myJoinStatus) {
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
                myJoinStatus,
                team.getCreatedAt(),
                reviewStats.count(),
                reviewStats.average(),
                team.getFoundedYear(),
                team.getTeamColor(),
                team.getFormation(),
                recordSummary
        );
    }
}
