package com.kickoff.be.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        @JsonProperty("hasTeam") boolean hasTeam,
        Long teamId
) {

    public static UserResponse of(User user, Team team) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                team != null,
                team == null ? null : team.getId()
        );
    }
}
