package com.kickoff.be.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 운영 CORS 오리진 목록 ({@code CORS_ALLOWED_ORIGINS}).
 *
 * 웹사이트가 붙으면서 <b>브라우저가 처음으로 실사용 클라이언트</b>가 된다. 네이티브 앱은
 * {@code Origin} 을 보내지 않아 이 설정과 무관했지만, 웹은 여기서 막히면 아무것도 못 한다.
 *
 * <b>비었을 때 전부 허용으로 남는 것이 이 클래스에서 가장 중요하다.</b> 도메인이 정해지기
 * 전에 좁혀 버리면 웹을 처음 띄우는 날 막히는데, 브라우저 CORS 오류는 <b>서버 로그에
 * 아무것도 남기지 않아</b> 원인을 찾기가 아주 어렵다.
 *
 * 스프링 컨텍스트를 띄우지 않는다 — 검증 대상이 설정 문자열을 어떻게 읽느냐이고, 그건
 * 생성자에 다 들어 있다.
 */
class CorsAllowedOriginsTest {

    @Test
    @DisplayName("비어 있으면 전부 허용 — 현행 유지")
    void emptyKeepsAllowAll() {
        assertThat(originsOf("")).containsExactly("*");
        assertThat(originsOf(null)).containsExactly("*");
        assertThat(originsOf("   ")).containsExactly("*");
    }

    @Test
    @DisplayName("목록이 있으면 그 오리진만 허용한다")
    void listNarrowsOrigins() {
        List<String> origins = originsOf("https://kickoff.app,https://www.kickoff.app");

        assertThat(origins).containsExactly("https://kickoff.app", "https://www.kickoff.app");
        assertThat(origins).doesNotContain("*");
    }

    @Test
    @DisplayName("공백을 넣어 적어도 된다 — env 는 사람이 손으로 친다")
    void trimsWhitespace() {
        assertThat(originsOf("https://a.com , https://b.com"))
                .containsExactly("https://a.com", "https://b.com");
    }

    /**
     * {@code "a.com,"} 처럼 콤마가 하나 더 붙는 실수가 흔하다. 빈 문자열이 패턴으로 들어가면
     * 어떤 오리진과도 안 맞는 규칙이 목록에 남아, 나중에 읽는 사람을 헷갈리게 한다.
     */
    @Test
    @DisplayName("빈 항목은 걸러낸다 — 콤마를 하나 더 찍는 실수가 흔하다")
    void dropsEmptyEntries() {
        assertThat(originsOf("https://a.com,,")).containsExactly("https://a.com");
        assertThat(originsOf(",,,")).as("전부 비면 현행 유지로 되돌아간다").containsExactly("*");
    }

    @Test
    @DisplayName("운영은 자격증명 허용이 꺼져 있다 — 헤더 인증이라 켤 이유가 없다")
    void prodDisablesCredentials() {
        assertThat(configOf("prod", "").getAllowCredentials()).isFalse();
        assertThat(configOf("dev", "").getAllowCredentials()).isTrue();
    }

    /** 웹에서 토큰을 실어 보내려면 이 헤더가 열려 있어야 한다. */
    @Test
    @DisplayName("Authorization 헤더가 허용된다")
    void allowsAuthorizationHeader() {
        assertThat(configOf("prod", "https://kickoff.app").getAllowedHeaders())
                .containsExactly("*");
    }

    private List<String> originsOf(String raw) {
        return configOf("prod", raw).getAllowedOriginPatterns();
    }

    private CorsConfiguration configOf(String profile, String raw) {
        UrlBasedCorsConfigurationSource source =
                (UrlBasedCorsConfigurationSource) new CorsConfig(profile, raw)
                        .corsConfigurationSource();
        return source.getCorsConfigurations().get("/**");
    }
}
