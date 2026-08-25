package com.kickoff.be.auth.dto;

import com.kickoff.be.user.dto.UserResponse;

/**
 * 로그인·가입 응답 (계약서 §3). v1.7.0 부터 두 토큰을 함께 준다.
 *
 * refreshToken 이 늘어난 건 비파괴 변경이라, 이 필드를 모르는 구버전 FE 도 그대로 돈다.
 * 다만 access 만료가 7일에서 1시간으로 짧아져 로그인 유지 시간은 짧아진다.
 */
public record AuthResponse(String accessToken, String refreshToken, UserResponse user) {
}
