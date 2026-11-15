package com.kickoff.be.auth.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * refresh token 쿠키의 속성 (계약서 §3 「웹의 토큰 전달」, v1.32.0).
 *
 * @param secure   {@code Secure} 를 붙일지. 기본 운영 true / 개발 false —
 *                 개발은 {@code http://localhost} 라 Secure 를 붙이면 브라우저가 쿠키를
 *                 저장하지 않는다. <b>운영에서 끄지 말 것</b>: 평문 구간이 한 번이라도
 *                 있으면 30일짜리 토큰이 그대로 네트워크에 흐른다
 * @param sameSite {@code Strict}(기본)·{@code Lax}·{@code None}. 웹과 API 가 같은 사이트에
 *                 있는 것이 전제라 Strict 로 충분하다. 다른 사이트로 가면 {@code None} 이
 *                 필요해지는데 그때는 방어가 한 겹 얇아진다
 * @param domain   비우면 요청 호스트에만 붙는다(기본). 서브도메인끼리 공유해야 할 때만 쓴다 —
 *                 넓히면 그 도메인 아래 모든 호스트가 쿠키를 보게 된다
 */
@ConfigurationProperties(prefix = "auth.cookie")
public record AuthCookieProperties(boolean secure, String sameSite, String domain) {

    private static final String DEFAULT_SAME_SITE = "Strict";

    public AuthCookieProperties {
        sameSite = (sameSite == null || sameSite.isBlank()) ? DEFAULT_SAME_SITE : sameSite.trim();
        domain = (domain == null || domain.isBlank()) ? null : domain.trim();
    }

    /**
     * 브라우저가 아예 거부하는 조합인지 — {@code SameSite=None} 인데 {@code Secure} 가 없다.
     *
     * 이 경우 쿠키는 <b>설정되지 않는다.</b> 서버는 Set-Cookie 를 보냈고 응답도 200 이라
     * 로그만 봐서는 멀쩡한데, 웹 로그인만 새로고침에서 끊긴다.
     */
    public boolean isRejectedByBrowsers() {
        return "None".equalsIgnoreCase(sameSite) && !secure;
    }
}
