package com.kickoff.be.oauth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kickoff.be.oauth.service.SocialLoginService.SocialLoginResult;

/**
 * 웹 OAuth 일회용 코드 교환 응답 (계약서 §3-1, v1.26.0).
 *
 * 앱이 302 쿼리로 받던 것과 <b>같은 세 값</b>이다 — 전달 경로만 다르다. 그래서 웹 FE 도
 * 앱과 같은 후속 흐름(토큰 저장 → {@code GET /api/auth/me})을 그대로 쓴다.
 *
 * {@code isNewUser} 는 {@code is} 로 시작해 직렬화 이름이 {@code newUser} 로 깎일 수 있어
 * 명시한다 — 계약서 필드명이라 지우면 FE 가 깨진다.
 */
public record OAuthExchangeResponse(
        String accessToken,
        String refreshToken,
        @JsonProperty("isNewUser") boolean isNewUser
) {

    public static OAuthExchangeResponse of(SocialLoginResult result) {
        return new OAuthExchangeResponse(result.accessToken(), result.refreshToken(),
                result.newUser());
    }
}
