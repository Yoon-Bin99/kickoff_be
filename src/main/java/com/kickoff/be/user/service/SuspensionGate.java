package com.kickoff.be.user.service;

import com.kickoff.be.consent.service.ConsentGate;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.time.OffsetDateTime;
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
 * 이용 제한 중인 계정을 막는다 (계약서 §7-2 「이용 제한」, v1.35.0).
 *
 * <b>동의 게이트 바로 뒤에 선다.</b> 계약이 정한 순서가 인증(401) → 동의(403
 * {@code CONSENT_REQUIRED}) → 이용 제한(403 {@code ACCOUNT_SUSPENDED}) → 그 밖의 검증이다.
 * 뒤집으면 동의도 안 한 제한 계정이 "제한됐다"는 안내를 먼저 받는다.
 *
 * <b>이의 제기 창구는 열어 둔다.</b> 동의 게이트의 예외(탈퇴 포함)에 더해 고객센터의
 * 사용자 쪽 API 가 열린다 — 막으면 제한이 통보 없는 추방이 되고, 제한이 부당해도 말할
 * 곳이 없다. 운영자 쪽 문의함은 열지 않는다(그쪽은 §7-2 의 운영자 검사가 따로 본다).
 */
@Component
@RequiredArgsConstructor
public class SuspensionGate implements AuthorizationManager<RequestAuthorizationContext> {

    /** 제한 중에도 되는 것 — 고객센터 사용자 쪽 (계약서 §7-2). */
    private static final List<Exemption> EXEMPTIONS = List.of(
            new Exemption(HttpMethod.GET, "/api/support/faq"),
            new Exemption(HttpMethod.GET, "/api/support/chat"),
            new Exemption(HttpMethod.POST, "/api/support/chat"),
            new Exemption(HttpMethod.POST, "/api/support/chat/escalate"));

    private final ConsentGate consentGate;
    private final UserRepository userRepository;

    /**
     * 동의를 먼저 보고, 통과하면 제한을 본다.
     *
     * 두 판정을 한 매니저로 묶은 이유: 시큐리티의 {@code anyRequest()} 자리는 하나이고,
     * 그 자리의 뜻이 계약의 "인증이 필요한 API"다. 묶지 않으면 그 목록을 두 벌 적게 된다.
     */
    @Override
    @Transactional(readOnly = true)
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        AuthorizationResult consent = consentGate.authorize(authentication, context);
        if (consent == null || !consent.isGranted()) {
            return consent;
        }
        Authentication auth = authentication.get();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            return consent;
        }
        if (isExempt(context)) {
            return consent;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isSuspendedAt(OffsetDateTime.now())) {
            return consent;
        }
        return new SuspendedDecision(false);
    }

    /**
     * 제한 중에도 되는 것 = <b>동의의 예외 목록 + 고객센터</b> (계약서 §7-2).
     *
     * 앞의 목록을 여기서 다시 적지 않고 {@link ConsentGate#exempts} 를 그대로 부른다 —
     * 두 벌이면 한쪽만 고쳐지는 날이 오고, 그때 제한된 사람이 탈퇴하지 못하게 된다.
     */
    private boolean isExempt(RequestAuthorizationContext context) {
        String path = context.getRequest().getRequestURI();
        String method = context.getRequest().getMethod();
        return ConsentGate.exempts(method, path)
                || EXEMPTIONS.stream().anyMatch(exemption -> exemption.matches(method, path));
    }

    /**
     * 거부의 이유가 "이용 제한"임을 알리는 표식.
     *
     * 동의 거부와 같은 403 이라, 표식이 없으면 FE 가 제한된 계정을 동의 화면으로 보낸다.
     */
    public static final class SuspendedDecision extends AuthorizationDecision {
        public SuspendedDecision(boolean granted) {
            super(granted);
        }
    }

    /** 경로는 완전 일치다 — 접두어로 두면 운영자 쪽 문의함까지 함께 열린다. */
    private record Exemption(HttpMethod method, String path) {
        boolean matches(String requestMethod, String requestPath) {
            return method.matches(requestMethod) && path.equals(requestPath);
        }
    }
}
