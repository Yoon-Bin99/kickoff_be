package com.kickoff.be.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.team.AgeGroup;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.Team;
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
        OffsetDateTime createdAt
) {

    /** viewerId 는 비로그인이면 null — 그때 isMine 은 false. */
    public static TeamResponse of(Team team, Long viewerId) {
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
                team.getCreatedAt()
        );
    }
}
