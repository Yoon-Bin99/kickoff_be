package com.kickoff.be.auth.cookie;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.config.AllowedOrigins;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 쿠키 속성 설정 (계약서 §3 「웹의 토큰 전달」, v1.32.0).
 *
 * 스프링을 띄우지 않는다 — 보려는 것이 "어떤 설정에서 어떤 {@code Set-Cookie} 가
 * 나오는가"뿐이고, 운영 설정(Secure 켬)은 통합 테스트의 평문 MockMvc 로는 확인할 수 없다.
 */
class AuthCookiePropertiesTest {

    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    @Test
    @DisplayName("운영 설정은 Secure·HttpOnly·SameSite·Path 를 전부 단다")
    void productionCookieCarriesEveryAttribute() {
        String cookie = delivery(new AuthCookieProperties(true, "Strict", null))
                .issueCookie("rt-token-value");

        assertThat(cookie).contains("kickoff_rt=rt-token-value");
        assertThat(cookie).contains("HttpOnly");
        assertThat(cookie).contains("Secure");
        assertThat(cookie).contains("SameSite=Strict");
        assertThat(cookie).contains("Path=/api/auth");
        assertThat(cookie).contains("Max-Age=" + THIRTY_DAYS.toSeconds());
    }

    /** 개발은 http://localhost 라 Secure 를 붙이면 브라우저가 저장하지 않는다. */
    @Test
    @DisplayName("Secure 를 끄면 그 속성만 빠진다")
    void insecureCookieOmitsSecure() {
        String cookie = delivery(new AuthCookieProperties(false, "Strict", null))
                .issueCookie("rt-token-value");

        assertThat(cookie).doesNotContain("Secure");
        assertThat(cookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("도메인은 주었을 때만 붙는다")
    void domainIsOptional() {
        assertThat(delivery(new AuthCookieProperties(true, "Strict", null)).issueCookie("t"))
                .doesNotContain("Domain");
        assertThat(delivery(new AuthCookieProperties(true, "Strict", ".kickoff.example"))
                .issueCookie("t"))
                .contains("Domain=.kickoff.example");
    }

    /**
     * 지우는 쿠키는 <b>발급 때와 속성이 같아야 한다.</b> Path·Domain 이 하나라도 다르면
     * 브라우저는 다른 쿠키로 보고 원래 것을 그대로 둔다 — 로그아웃했는데 토큰이 남는다.
     */
    @Test
    @DisplayName("지우는 쿠키는 Max-Age=0 이고 속성이 같다")
    void clearingCookieMatchesIssuedAttributes() {
        AuthCookieProperties properties = new AuthCookieProperties(true, "Strict",
                ".kickoff.example");

        String cleared = delivery(properties).clearCookie();

        assertThat(cleared).contains("Max-Age=0");
        assertThat(cleared).contains("Path=/api/auth");
        assertThat(cleared).contains("Domain=.kickoff.example");
        assertThat(cleared).contains("HttpOnly");
        assertThat(cleared).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("빈 설정은 기본값으로 채워진다")
    void blanksFallBackToDefaults() {
        AuthCookieProperties properties = new AuthCookieProperties(true, "  ", "  ");

        assertThat(properties.sameSite()).isEqualTo("Strict");
        assertThat(properties.domain()).isNull();
    }

    /**
     * 브라우저가 아예 거부하는 조합이다. 서버는 Set-Cookie 를 보냈고 응답도 200 이라
     * 로그만 봐서는 멀쩡한데, 웹 로그인만 새로고침에서 끊긴다.
     */
    @Test
    @DisplayName("SameSite=None 인데 Secure 가 없으면 '브라우저가 거부함'으로 본다")
    void sameSiteNoneWithoutSecureIsFlagged() {
        assertThat(new AuthCookieProperties(false, "None", null).isRejectedByBrowsers()).isTrue();
        assertThat(new AuthCookieProperties(false, "none", null).isRejectedByBrowsers())
                .as("대소문자로 갈리면 안 된다").isTrue();
        assertThat(new AuthCookieProperties(true, "None", null).isRejectedByBrowsers()).isFalse();
        assertThat(new AuthCookieProperties(false, "Strict", null).isRejectedByBrowsers()).isFalse();
    }

    private WebTokenDelivery delivery(AuthCookieProperties properties) {
        return new WebTokenDelivery(properties, new AllowedOrigins(""), THIRTY_DAYS);
    }
}
