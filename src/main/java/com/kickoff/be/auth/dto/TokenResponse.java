package com.kickoff.be.auth.dto;

/**
 * POST /api/auth/refresh 응답 (계약서 §3, v1.7.0).
 *
 * 새 토큰 쌍만 준다 — user 는 없다. refresh 는 앱이 살아 있는 동안 조용히 도는 호출이라
 * 프로필까지 실어 보내면 매번 팀·소셜 연동을 조회하게 된다. 프로필이 필요하면 FE 가
 * /api/auth/me 를 부른다.
 */
public record TokenResponse(String accessToken, String refreshToken) {
}
