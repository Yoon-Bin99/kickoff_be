package com.kickoff.be.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;

/**
 * {@code CORS_ALLOWED_ORIGINS} 를 읽어 두고 "이 오리진이 허용인가"를 답한다.
 *
 * <b>두 곳이 같은 답을 써야 해서 따로 뺐다.</b> 브라우저에게 주는 CORS 응답
 * ({@code CorsConfig})과 쿠키를 쓰는 경로의 요청 위조 방어(계약서 §3 「웹의 토큰 전달」의
 * Origin 검사)가 같은 목록으로 판정해야 한다 — 한쪽만 통과하는 오리진이 생기면 브라우저는
 * 보내는데 서버가 403 이거나, 반대로 서버는 받는데 응답을 브라우저가 버린다. 둘 다
 * <b>원인을 찾기 어려운 방식으로</b> 막힌다.
 *
 * 판정은 {@link CorsConfiguration#checkOrigin} 에 맡긴다. 직접 문자열을 비교하면
 * {@code *} 와일드카드 규칙(스킴·호스트·포트 각각)을 다시 구현하게 되고, 그 구현이 스프링과
 * 한 글자라도 다르면 위의 어긋남이 그대로 생긴다.
 */
@Component
public class AllowedOrigins {

    /** 목록이 비었을 때의 값. "지금까지처럼 전부 허용"이다. */
    private static final List<String> ALLOW_ALL = List.of("*");

    private final List<String> patterns;
    private final boolean configured;
    private final CorsConfiguration matcher;

    public AllowedOrigins(@Value("${CORS_ALLOWED_ORIGINS:}") String allowedOrigins) {
        List<String> parsed = parse(allowedOrigins);
        this.configured = !parsed.isEmpty();
        this.patterns = configured ? parsed : ALLOW_ALL;
        this.matcher = new CorsConfiguration();
        this.matcher.setAllowedOriginPatterns(this.patterns);
    }

    /**
     * 목록이 주어졌는지. <b>"전부 허용"과 "명시적으로 좁혔다"를 가른다.</b>
     *
     * 계약서 §3 이 "허용 목록이 비어 있는 dev 에서는 Origin 검사를 하지 않는다"고 정한
     * 지점이고, 운영에서 이 값이 false 면 웹 로그인이 유지되지 않는다(쿠키를 못 준다).
     */
    public boolean isConfigured() {
        return configured;
    }

    /** 빈 목록이면 어떤 오리진이든 true — 그 상태의 뜻이 "전부 허용"이다. */
    public boolean matches(String origin) {
        return origin != null && matcher.checkOrigin(origin) != null;
    }

    public List<String> patterns() {
        return patterns;
    }

    /**
     * 빈 항목을 걸러내는 게 중요하다. {@code "a.com,"} 처럼 콤마가 하나 더 붙는 실수가
     * 흔한데, 빈 문자열이 패턴으로 들어가면 어떤 오리진과도 안 맞는 규칙이 하나 생긴다 —
     * 조용히 무시되는 게 아니라 목록에 남아 나중에 읽는 사람을 헷갈리게 한다.
     */
    private static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
