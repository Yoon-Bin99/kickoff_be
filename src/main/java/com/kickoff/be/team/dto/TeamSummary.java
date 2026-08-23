package com.kickoff.be.team.dto;

import com.kickoff.be.team.AgeGroup;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.Team;

/** 목록/카드에 박히는 축약형 (계약서 §2). owner 를 건드리지 않는다. */
public record TeamSummary(
        Long id,
        String name,
        String region,
        SkillLevel skillLevel,
        AgeGroup ageGroup,
        int memberCount,
        String logoUrl
) {

    public static TeamSummary from(Team team) {
        return new TeamSummary(
                team.getId(),
                team.getName(),
                team.getRegion(),
                team.getSkillLevel(),
                team.getAgeGroup(),
                team.getMemberCount(),
                team.getLogoUrl()
        );
    }
}
