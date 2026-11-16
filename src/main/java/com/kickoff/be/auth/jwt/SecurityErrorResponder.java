package com.kickoff.be.auth.jwt;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.consent.service.ConsentGate;
import com.kickoff.be.user.service.SuspensionGate;
import com.kickoff.be.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 시큐리티 필터 단계에서 걸린 인증/인가 실패도 ApiExceptionHandler 를 거치게 해서
 * 컨트롤러에서 난 예외와 완전히 같은 형식으로 내려보낸다.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityErrorResponder(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        resolver.resolveException(request, response, null, new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    /**
     * 인가 거부는 전부 여기로 모인다 — 그래서 <b>이유를 가려야 한다.</b>
     *
     * 동의를 마치지 않은 계정의 호출은 403 `CONSENT_REQUIRED` 다(계약서 §3 「동의」,
     * v1.34.0). 같은 403 이지만 FE 가 할 일이 다르다 — 보통의 403 은 그대로 두고, 이쪽은
     * {@code GET /api/auth/me} 를 다시 읽어 동의 화면으로 보낸다.
     *
     * 거부한 쪽({@code ConsentGate})이 표식을 남기므로 여기서 추측하지 않는다. 이 자리에서
     * 다시 판정하면 같은 규칙이 두 곳에 생긴다.
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null,
                new BusinessException(denialCode(accessDeniedException)));
    }

    /**
     * 거부의 이유를 에러 코드로 옮긴다.
     *
     * 세 가지가 모두 403 이라 <b>표식이 없으면 FE 가 가를 수 없다</b> — 동의는 동의
     * 화면으로, 이용 제한은 안내 화면으로, 그 밖에는 그대로 둬야 한다. 판정을 여기서
     * 다시 하지 않고 게이트가 남긴 표식만 본다.
     */
    private static ErrorCode denialCode(AccessDeniedException exception) {
        if (!(exception instanceof AuthorizationDeniedException denied)) {
            return ErrorCode.FORBIDDEN;
        }
        Object result = denied.getAuthorizationResult();
        if (result instanceof ConsentGate.ConsentDecision) {
            return ErrorCode.CONSENT_REQUIRED;
        }
        if (result instanceof SuspensionGate.SuspendedDecision) {
            return ErrorCode.ACCOUNT_SUSPENDED;
        }
        return ErrorCode.FORBIDDEN;
    }
}
