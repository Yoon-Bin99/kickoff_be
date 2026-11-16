package com.kickoff.be.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.common.ModerationResponse;
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
        RecordSummary recordSummary,

        /**
         * 팀 소개에 걸린 운영 조치 (계약서 §7-2, v1.35.0). 조치가 없으면 null 이고,
         * 있으면 그때 {@code introduction} 은 null 이다.
         *
         * <b>팀 전체가 아니라 소개만</b> 가려진다 — 팀 페이지 자체는 열린다.
         */
        ModerationResponse introductionModeration,

        /**
         * 내가 이 팀을 차단했는지 (계약서 §7-2, v1.35.0). 비로그인은 false.
         *
         * FE 는 이 값이 true 면 팀 페이지에 가림막과 "차단 해제"를 보인다 — 차단한 팀의
         * 페이지는 <b>열린다</b>. 닫아 버리면 해제할 길이 사라진다.
         */
        @JsonProperty("blockedByMe") boolean blockedByMe
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
                                  JoinStatus myJoinStatus, boolean blockedByMe) {
        // 가려진 소개는 감독 본인에게도 보이지 않는다 (계약서 §7-2) — 글과 다른 점이다.
        // 글 상세는 작성자가 자기 글을 확인·이의 제기해야 해서 열어 두지만, 소개는 그
        // 자리에 조치 표시가 서고 원문은 팀 수정 화면에서도 쓰지 못한다(410).
        ModerationResponse introductionModeration =
                ModerationResponse.of(team.getIntroductionModeration());
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getRegion(),
                team.getHomeGround(),
                team.getSkillLevel(),
                team.getAgeGroup(),
                team.getMemberCount(),
                introductionModeration == null ? team.getIntroduction() : null,
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
                recordSummary,
                introductionModeration,
                blockedByMe
        );
    }
}
