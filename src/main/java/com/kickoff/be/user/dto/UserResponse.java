package com.kickoff.be.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.List;

public record UserResponse(
        Long id,
        String email,
        String nickname,
        String phone,
        /** 주요 활동 지역 (v1.6.0). null 이면 전국 — FE 홈 목록이 지역 필터 없이 뜬다. */
        String activityRegion,
        @JsonProperty("hasTeam") boolean hasTeam,
        Long teamId,
        List<AuthProvider> authProviders,
        /**
         * 약관·개인정보처리방침 동의 시각 (계약서 §3-1, v1.25.0).
         *
         * <b>{@code null} 이 정상 상태다.</b> 소셜 가입은 이 값을 남기지 않고, v1.21.0 이전에
         * 가입한 계정도 없다. FE 는 이 값이 null 이거나 현행 시행일보다 이전이면 동의 화면을
         * 띄운다 — 그래서 서버가 채워 주지 않고 있는 그대로 내보내는 것이 중요하다.
         */
        OffsetDateTime termsAgreedAt
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
                user.getActivityRegion(),
                team != null,
                team == null ? null : team.getId(),
                authProviders == null ? List.of() : authProviders,
                user.getTermsAgreedAt()
        );
    }
}
