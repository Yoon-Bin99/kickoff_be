package com.kickoff.be.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        @JsonProperty("hasTeam") boolean hasTeam,
        Long teamId,
        List<AuthProvider> authProviders
) {

    /**
     * phone 은 소셜 가입 직후 null 일 수 있고, authProviders 는 이메일 가입만 했으면 빈 배열이다
     * (계약서 §2). 연동 목록은 별도 조회라 호출자가 넘긴다 — DTO 가 리포지터리를 잡지 않는다.
     */
    public static UserResponse of(User user, Team team, List<AuthProvider> authProviders) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                team != null,
                team == null ? null : team.getId(),
                authProviders == null ? List.of() : authProviders
        );
    }
}
