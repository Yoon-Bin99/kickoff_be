package com.kickoff.be.auth.dto;

import com.kickoff.be.user.dto.UserResponse;

/**
 * 로그인·가입 응답 (계약서 §3). v1.7.0 부터 두 토큰을 함께 준다.
 *
 * refreshToken 이 늘어난 건 비파괴 변경이라, 이 필드를 모르는 구버전 FE 도 그대로 돈다.
 * 다만 access 만료가 7일에서 1시간으로 짧아져 로그인 유지 시간은 짧아진다.
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {

    /**
     * refreshToken 을 뺀 사본 (계약서 §3 「웹의 토큰 전달」, v1.32.0).
     *
     * 웹에는 그 값이 본문이 아니라 HttpOnly 쿠키로 간다. <b>칸을 지우지 않고 null 로
     * 둔다</b> — 키가 통째로 사라지면 "그 필드를 모르는 서버"와 구별되지 않는다.
     */
    public AuthResponse withoutRefreshToken() {
        return new AuthResponse(accessToken, null, user);
    }
}
