package com.kickoff.be.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Expo 개발 서버(http://localhost:8081, exp://…)와 안드로이드 에뮬레이터(10.0.2.2)를 받아야 해서
 * 개발에서는 오리진을 열어둔다.
 *
 * <b>운영 오리진은 {@code CORS_ALLOWED_ORIGINS} 로 좁힌다.</b> 비워 두면 지금까지처럼
 * 전부 허용한다 — 웹사이트 도메인이 정해지기 전에는 좁힐 대상이 없고, 여기서 미리
 * 좁혀 두면 도메인이 붙는 날 <b>원인을 알기 어려운 방식으로</b> 막힌다(브라우저 콘솔의
 * CORS 오류는 서버 로그에 아무것도 남기지 않는다).
 *
 * 네이티브 앱은 {@code Origin} 을 보내지 않아 이 설정과 무관하다. 좁히는 것이 지키는
 * 대상은 <b>브라우저에서 도는 클라이언트</b>뿐이다.
 */
@Configuration
public class CorsConfig {

    /** 지금까지의 동작. 목록이 비면 이 값을 쓴다. */
    private static final List<String> ALLOW_ALL = List.of("*");

    private final boolean allowCredentials;
    private final List<String> allowedOriginPatterns;

    /**
     * 운영에서는 자격증명 허용을 끈다.
     *
     * 인증이 {@code Authorization} 헤더라 이 값이 {@code true} 일 이유가 없다. 브라우저가
     * 자동으로 붙이는 자격증명(쿠키·기본 인증)을 우리는 하나도 쓰지 않는다. 반면
     * <b>{@code *} 오리진과 함께 두면</b> 나중에 세션 쿠키를 하나라도 도입하는 순간 아무
     * 사이트나 그 쿠키를 태워 우리 API 를 부를 수 있는 상태가 된다.
     *
     * 개발에서는 켜 둔다. 브라우저로 이것저것 붙여 보는 자리라 굳이 막을 이유가 없다.
     *
     * @param allowedOrigins 콤마로 구분한 오리진 패턴. 빈 값이면 전부 허용(현행 유지).
     *                       {@code *} 를 와일드카드로 쓸 수 있다 — {@code setAllowedOriginPatterns}
     *                       가 받는 형식 그대로다.
     */
    public CorsConfig(@Value("${spring.profiles.active:dev}") String activeProfile,
                      @Value("${CORS_ALLOWED_ORIGINS:}") String allowedOrigins) {
        this.allowCredentials = !"prod".equals(activeProfile);
        this.allowedOriginPatterns = parse(allowedOrigins);
    }

    /**
     * 빈 항목을 걸러내는 게 중요하다. {@code "a.com,"} 처럼 콤마가 하나 더 붙는 실수가
     * 흔한데, 빈 문자열이 패턴으로 들어가면 어떤 오리진과도 안 맞는 규칙이 하나 생긴다 —
     * 조용히 무시되는 게 아니라 목록에 남아 나중에 읽는 사람을 헷갈리게 한다.
     */
    private static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ALLOW_ALL;
        }
        List<String> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return parsed.isEmpty() ? ALLOW_ALL : parsed;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Location"));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
