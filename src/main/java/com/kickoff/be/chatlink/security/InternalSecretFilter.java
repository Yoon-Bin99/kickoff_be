package com.kickoff.be.chatlink.security;

import com.kickoff.be.auth.jwt.SecurityErrorResponder;
import com.kickoff.be.chatlink.client.ChatLinkProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /internal/**} 의 유일한 자격 검사 (계약서 §10).
 *
 * <b>비밀값이 없으면 전부 막는다.</b> 설정을 빠뜨린 배포가 내부 API 를 열어 두는 쪽보다,
 * 채팅이 안 되는 쪽이 낫다 — 계약서가 "닫힌 쪽으로 실패"라고 못박은 지점이다.
 *
 * <b>비교는 상수 시간이다.</b> {@code equals} 는 다른 첫 글자에서 바로 빠져나오므로,
 * 응답 시간을 재면서 한 글자씩 맞춰 가는 공격이 성립한다. 길이가 다르면 그 사실은 새지만
 * (비교 전에 갈린다) 내용은 새지 않는다.
 *
 * <b>JWT 가 있어도 통과하지 못한다.</b> 사용자 토큰은 이 경로와 아무 상관이 없다 —
 * 틀린 비밀값과 똑같이 401 이다. 그래서 이 필터는 {@code JwtAuthenticationFilter} 보다
 * <b>먼저</b> 등록한다(SecurityConfig 참고): 여기서 먼저 인증을 심어 두면 JWT 필터는
 * 이미 인증이 있는 요청을 건드리지 않고, 실패하면 애초에 체인이 더 가지 않는다.
 *
 * 통과한 요청에는 {@code ROLE_INTERNAL} 을 심는다. 그 자체로 꼭 필요하지는 않지만,
 * SecurityConfig 의 {@code hasRole("INTERNAL")} 과 짝이 되어 <b>필터가 어떤 이유로든
 * 빠지면 경로가 열리는 게 아니라 닫히게</b> 한다.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Secret";

    /** 계약서 §10 — 경로는 {@code /internal/**}, {@code /api} 프리픽스가 없다. */
    public static final String PATH_PREFIX = "/internal/";

    public static final String ROLE = "INTERNAL";

    private final ChatLinkProperties properties;
    private final SecurityErrorResponder errorResponder;

    public InternalSecretFilter(ChatLinkProperties properties,
                                SecurityErrorResponder errorResponder) {
        this.properties = properties;
        this.errorResponder = errorResponder;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!matches(request.getHeader(HEADER))) {
            // 체인을 더 보내지 않는다. 계약서가 정한 401 UNAUTHORIZED 를 다른 인증 실패와
            // 같은 형식으로 내보낸다.
            errorResponder.commence(request, response, null);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("chat-server", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        filterChain.doFilter(request, response);
    }

    private boolean matches(String provided) {
        if (!properties.hasSecret() || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                properties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
