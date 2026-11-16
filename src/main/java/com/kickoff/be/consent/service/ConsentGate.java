package com.kickoff.be.consent.service;

import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동의를 마치지 않은 계정의 호출을 막는다 (계약서 §3 「동의」, v1.34.0).
 *
 * <b>시큐리티의 인가 규칙으로 걸었다.</b> SecurityConfig 에서 {@code anyRequest()} 자리에
 * 들어가는데, 그 자리의 뜻이 정확히 계약의 "인증이 필요한 API"다 — 인증이 선택인 조회
 * API(모집글 목록·상세, 팀 조회 등)는 모두 그 위에서 {@code permitAll} 로 걸러져 여기 오지
 * 않는다.
 *
 * <b>그 점이 이 자리를 고른 이유다.</b> 필터나 인터셉터로 만들면 "인증이 필요한 API"가
 * 무엇인지 다시 적어야 하고, 그 목록이 SecurityConfig 와 어긋나는 날 <b>조용히</b> 틀린다 —
 * 막아야 할 API 가 열리거나, 토큰을 들고 목록을 보는 사용자가 403 을 받는다. 여기서는
 * permitAll 목록이 유일한 진실로 남는다.
 *
 * 검사 순서도 자연히 맞는다 — 인증(401)이 먼저고, 통과한 뒤 이 판정(403)이며, 요청 본문
 * 검증은 컨트롤러에 닿은 다음이다(계약서 §3).
 *
 * <b>{@code /internal/**} 은 대상이 아니다.</b> 그 경로는 이 규칙보다 앞에서
 * {@code hasRole("INTERNAL")} 로 끊기고(§10), 자격이 공유 비밀이라 사용자가 없다 —
 * 동의를 물을 대상 자체가 없다.
 */
@Component
@RequiredArgsConstructor
public class ConsentGate implements AuthorizationManager<RequestAuthorizationContext> {

    /**
     * 동의 전에도 되는 것 (계약서 §3). 넷은 동의 화면이 살아 있기 위한 길이고, 둘은
     * <b>동의하지 않고 떠날 길</b>이다 — 그 길이 막히면 동의가 강요가 된다.
     */
    private static final List<Exemption> EXEMPTIONS = List.of(
            // 동의 화면이 무엇을 받아야 하는지 읽는다
            new Exemption(HttpMethod.GET, "/api/auth/me"),
            // 토큰 유지 — 동의 화면에 머무는 동안 access 가 만료될 수 있다
            new Exemption(HttpMethod.POST, "/api/auth/refresh"),
            new Exemption(HttpMethod.POST, "/api/auth/logout"),
            // 동의 제출 자체
            new Exemption(HttpMethod.PUT, "/api/users/me/consents"),
            // 탈퇴와 그 앞의 비밀번호 확인
            new Exemption(HttpMethod.DELETE, "/api/users/me"),
            new Exemption(HttpMethod.POST, "/api/users/me/verify-password"));

    private final UserRepository userRepository;
    private final ConsentService consentService;

    @Override
    @Transactional(readOnly = true)
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                        RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || isAnonymous(auth)) {
            // 인증이 먼저다 (계약서 §3 — 401 → 403). 여기서 거부하면 시큐리티가
            // authenticationEntryPoint 로 보내 401 이 나간다.
            return new AuthorizationDecision(false);
        }
        if (isExempt(context)) {
            return new AuthorizationDecision(true);
        }
        if (!(auth.getPrincipal() instanceof Long userId)) {
            // JwtAuthenticationFilter 가 넣는 principal 은 userId 다. 다른 것이 들어오는
            // 경로(예: /internal 의 ROLE_INTERNAL)는 이 규칙까지 오지 않지만, 와도
            // 동의를 물을 사용자가 없으므로 통과시킨다 — 막으면 그쪽이 조용히 403 이 된다.
            return new AuthorizationDecision(true);
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            // 탈퇴 직후 남은 토큰이다. 동의로 막을 일이 아니라 각 API 가 자기 규칙으로
            // 답하게 둔다(계약서 §10 의 "탈퇴 직후 토큰" 처리와 같은 태도다).
            return new AuthorizationDecision(true);
        }
        return new ConsentDecision(!consentService.isRequiredFor(user));
    }

    private static boolean isAnonymous(Authentication auth) {
        return auth instanceof org.springframework.security.authentication
                .AnonymousAuthenticationToken;
    }

    private boolean isExempt(RequestAuthorizationContext context) {
        String path = context.getRequest().getRequestURI();
        String method = context.getRequest().getMethod();
        return EXEMPTIONS.stream().anyMatch(exemption -> exemption.matches(method, path));
    }

    /**
     * 거부의 이유가 "동의"임을 알리는 표식.
     *
     * 시큐리티의 인가 거부는 모두 한 핸들러로 모이는데, 그쪽에서 403 `FORBIDDEN` 과
     * 403 `CONSENT_REQUIRED` 를 가려야 한다 — FE 가 할 일이 다르다(전자는 그대로 두고,
     * 후자는 동의 화면으로 보낸다).
     */
    public static final class ConsentDecision extends AuthorizationDecision {
        public ConsentDecision(boolean granted) {
            super(granted);
        }
    }

    /** 경로는 완전 일치다 — 접두어로 두면 하위 경로가 함께 열린다. */
    private record Exemption(HttpMethod method, String path) {
        boolean matches(String requestMethod, String requestPath) {
            return method.matches(requestMethod) && path.equals(requestPath);
        }
    }
}
