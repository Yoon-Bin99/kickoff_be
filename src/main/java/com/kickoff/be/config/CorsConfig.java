package com.kickoff.be.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
@Slf4j
@Configuration
public class CorsConfig {

    private final AllowedOrigins allowedOrigins;
    private final boolean allowCredentials;

    /**
     * 자격증명(쿠키) 허용 여부 — v1.32.0 에서 뜻이 바뀌었다.
     *
     * 전에는 "운영에서는 끈다"였다. 인증이 {@code Authorization} 헤더뿐이라 켤 이유가
     * 없었기 때문이다. 이제 <b>웹이 refresh token 을 HttpOnly 쿠키로 받으므로</b>
     * (계약서 §3 「웹의 토큰 전달」) 켜지 않으면 브라우저가 그 쿠키를 저장하지 않는다 —
     * 웹 로그인이 새로고침에서 끊긴다.
     *
     * <b>다만 "전부 허용"과 함께 켜지는 않는다.</b> 목록이 비어 있으면 스프링은 요청
     * Origin 을 그대로 반향하는데, 거기에 자격증명까지 허용하면 <b>아무 사이트나 사용자의
     * 쿠키를 태워 우리 API 를 부를 수 있다.</b> 그래서 운영에서 목록이 비어 있으면 끈다 —
     * 계약서가 "운영에서는 CORS_ALLOWED_ORIGINS 가 반드시 있어야 한다"고 적은 이유이고,
     * 그 상태의 증상(웹 로그인이 유지되지 않음)도 계약서에 그대로 있다.
     *
     * 개발은 켠다. 목록이 비어 있어도 {@code setAllowedOriginPatterns} 를 쓰므로 스프링이
     * {@code *} 가 아니라 <b>요청 Origin 을 그대로 돌려준다</b> — 자격증명과 함께 쓸 수
     * 있는 형태다. 브라우저로 이것저것 붙여 보는 자리라 막을 이유가 없다.
     *
     * <b>프로파일을 {@code @Value("${spring.profiles.active}")} 로 읽지 않는다.</b> 그
     * 프로퍼티는 yaml·환경변수로 프로파일을 줄 때만 채워진다. 테스트는
     * {@code @ActiveProfiles} 로 띄우는데 그 경로는 프로퍼티를 채우지 않아서, 기본값
     * {@code dev} 로 떨어져 <b>운영 가지가 테스트에서 한 번도 돌지 않는 상태</b>가 된다.
     * {@link Environment#matchesProfiles} 는 실제 활성 프로파일을 본다.
     */
    public CorsConfig(Environment environment, AllowedOrigins allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
        boolean production = environment.matchesProfiles("prod");
        this.allowCredentials = allowedOrigins.isConfigured() || !production;

        if (production && !allowedOrigins.isConfigured()) {
            log.warn("CORS_ALLOWED_ORIGINS 가 비어 있다 — 쿠키를 허용할 수 없어 "
                    + "웹 로그인이 새로고침에서 끊긴다(계약서 §3). 앱은 영향 없다. "
                    + "웹 도메인을 넣고 다시 띄울 것");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(allowedOrigins.patterns());
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        // "*" 는 자격증명과 함께 쓸 수 없는 값이지만, 스프링은 이 자리에서 그 글자를
        // 그대로 내보내지 않고 <b>요청이 요구한 헤더를 그대로 돌려준다.</b> 그래서
        // X-Kickoff-Client(계약서 §3, v1.32.0)와 X-Internal-Secret(§10)이 따로 적지 않아도
        // 통과한다. 목록을 좁히는 날에는 그 둘을 빠뜨리지 말 것 — 빠지면 브라우저가
        // 프리플라이트에서 막고, 서버 로그에는 아무것도 남지 않는다.
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Location"));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
