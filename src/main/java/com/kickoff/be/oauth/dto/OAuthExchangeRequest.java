package com.kickoff.be.oauth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 웹 OAuth 일회용 코드 교환 요청 (계약서 §3-1, v1.26.0).
 *
 * <b>본문으로 받는다.</b> 쿼리로 받으면 이 요청 자체가 서버 접근 로그·프록시 로그에
 * 코드를 남긴다 — 토큰을 URL 에서 빼내려고 만든 절차인데 같은 자리에 다시 두는 셈이다.
 */
public record OAuthExchangeRequest(
        @NotBlank(message = "코드는 필수입니다.")
        String code
) {
}
