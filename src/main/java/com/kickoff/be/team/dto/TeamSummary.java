package com.kickoff.be.team.dto;

import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import com.kickoff.be.team.entity.Team;

/**
 * 목록/카드에 박히는 축약형 (계약서 §2). owner 를 건드리지 않는다.
 *
 * v1.10.0 부터 평점이 함께 실린다. 집계는 <b>호출자가 넘긴다</b> — 여기서 조회하면
 * 리포지터리 의존이 DTO 로 새고, 무엇보다 카드마다 쿼리가 나가 목록에서 N+1 이 된다
 * (계약서가 배치를 못박은 이유다).
 *
 * 기본값을 채워 주는 생성자를 두지 않은 것도 의도다. 그런 게 있으면 다음 사람이 무심코
 * 써서 평점 4.5 인 팀이 어떤 화면에서만 "평가 없음"으로 나가는데, 에러가 아니라 조용히
 * 틀린 값이라 화면을 보기 전에는 아무도 모른다. TeamResponse 에서 같은 이유로 같은
 * 결정을 했다.
 */
public record TeamSummary(
        Long id,
        String name,
        String region,
        SkillLevel skillLevel,
        AgeGroup ageGroup,
        int memberCount,
        String logoUrl,
        long reviewCount,
        Double averageRating
) {

    public static TeamSummary of(Team team, ReviewStats stats) {
        return new TeamSummary(
                team.getId(),
                team.getName(),
                team.getRegion(),
                team.getSkillLevel(),
                team.getAgeGroup(),
                team.getMemberCount(),
                team.getLogoUrl(),
                stats.count(),
                stats.average()
        );
    }
}
