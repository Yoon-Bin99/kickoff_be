package com.kickoff.be.config;

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
 * <b>오리진은 운영에서도 아직 열려 있다.</b> 좁히려면 앱이 실제로 쓰는 스킴만 남겨야 하는데,
 * 웹 미리보기를 운영 서버에 붙여 확인하는 개발 관례가 있어 지금 좁히면 그 작업이 끊긴다.
 * 웹을 정식 배포하는 시점에 도메인으로 좁힌다 — README 「운영 배포」에 같은 내용을 적어 뒀다.
 */
@Configuration
public class CorsConfig {

    private final boolean allowCredentials;

    /**
     * 운영에서는 자격증명 허용을 끈다.
     *
     * 인증이 {@code Authorization} 헤더라 이 값이 {@code true} 일 이유가 없다. 브라우저가
     * 자동으로 붙이는 자격증명(쿠키·기본 인증)을 우리는 하나도 쓰지 않는다. 반면
     * <b>{@code *} 오리진과 함께 두면</b> 나중에 세션 쿠키를 하나라도 도입하는 순간 아무
     * 사이트나 그 쿠키를 태워 우리 API 를 부를 수 있는 상태가 된다 — 그때는 이 설정이
     * 원인이라는 걸 알아채기 어렵다. 위험을 미리 끊어 둔다.
     *
     * 앱 동작은 그대로다. 네이티브 앱은 {@code Origin} 을 보내지 않아 CORS 대상이 아니고,
     * 웹에서도 헤더 인증은 {@code allowCredentials} 와 무관하다.
     *
     * 개발에서는 켜 둔다. 브라우저로 이것저것 붙여 보는 자리라 굳이 막을 이유가 없다.
     */
    public CorsConfig(@Value("${spring.profiles.active:dev}") String activeProfile) {
        this.allowCredentials = !"prod".equals(activeProfile);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
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
