package com.kickoff.be.auth.cookie;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.config.AllowedOrigins;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 웹에게는 refresh token 을 본문이 아니라 HttpOnly 쿠키로 준다 (계약서 §3, v1.32.0).
 *
 * <b>왜.</b> 브라우저에서 refresh token 을 스크립트가 읽을 수 있는 곳(localStorage)에 두면,
 * 페이지에 악성 스크립트가 한 번 심기는 것으로 30일짜리 토큰을 통째로 잃는다. HttpOnly 는
 * 스크립트가 아예 읽지 못하게 한다. 앱(네이티브)은 기기 보안 저장소를 쓰므로 바뀌지 않는다.
 *
 * <b>어느 쪽인지는 클라이언트가 밝힌다</b> — {@code X-Kickoff-Client: web}. 헤더가 없거나
 * 다른 값이면 앱으로 보고 v1.31.4 와 똑같이 동작한다. 서버가 User-Agent 로 추측하지 않는
 * 이유는, 추측이 틀리는 날 <b>토큰이 어디에도 없는</b> 응답이 나가기 때문이다.
 *
 * <b>{@code Path} 를 좁힌다.</b> {@code /api/auth} 아래에만 실리므로 일반 API 요청에는
 * 쿠키가 따라붙지 않는다 — 그쪽 인증은 계속 {@code Authorization} 헤더뿐이고, 쿠키로
 * 인증되는 경로는 refresh·logout 둘이다. 요청 위조가 닿을 수 있는 표면을 그만큼 줄인다.
 */
@Slf4j
@Component
public class WebTokenDelivery {

    /** 계약서 §3 — 웹 FE 가 붙이는 헤더. 값이 정확히 {@code web} 일 때만 웹이다. */
    public static final String CLIENT_HEADER = "X-Kickoff-Client";
    public static final String WEB_CLIENT = "web";

    public static final String COOKIE_NAME = "kickoff_rt";

    /** 쿠키가 실리는 범위. refresh·logout 이 여기 있다. */
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthCookieProperties properties;
    private final AllowedOrigins allowedOrigins;
    private final Duration refreshExpiration;

    public WebTokenDelivery(AuthCookieProperties properties, AllowedOrigins allowedOrigins,
                            @Value("${jwt.refresh-expiration}") Duration refreshExpiration) {
        this.properties = properties;
        this.allowedOrigins = allowedOrigins;
        this.refreshExpiration = refreshExpiration;

        if (properties.isRejectedByBrowsers()) {
            // 기동을 막지 않는다. 틀린 것은 <b>웹 쿠키 하나</b>고, 앱·채팅 서버·나머지 API 는
            // 아무 영향이 없다 — 여기서 세우면 쿠키를 쓰지도 않는 사용자까지 서비스를 잃는다.
            // (시드 가드는 반대로 기동을 막는다. 그쪽은 운영 DB 에 계정이 생기는 일이라
            //  대가가 비교가 안 된다.)
            log.warn("AUTH_COOKIE_SAMESITE=None 인데 AUTH_COOKIE_SECURE 가 꺼져 있다 — "
                    + "브라우저가 이 쿠키를 저장하지 않는다. 응답은 200 이고 로그도 멀쩡한데 "
                    + "웹 로그인만 새로고침에서 끊긴다. Secure 를 켜거나 SameSite 를 되돌릴 것");
        }
    }

    /** 이 요청이 웹인가. 헤더가 없으면 앱이다 — 기본값이 "지금까지의 동작"이어야 한다. */
    public boolean isWeb(HttpServletRequest request) {
        return request != null && WEB_CLIENT.equalsIgnoreCase(request.getHeader(CLIENT_HEADER));
    }

    /** 새로 발급한 refresh token 을 담은 {@code Set-Cookie} 값. */
    public String issueCookie(String refreshToken) {
        return cookie(refreshToken, refreshExpiration).toString();
    }

    /**
     * 쿠키를 지우는 {@code Set-Cookie} 값 ({@code Max-Age=0}).
     *
     * <b>속성이 발급 때와 같아야 한다.</b> Path·Domain 이 하나라도 다르면 브라우저는 다른
     * 쿠키로 보고 원래 것을 그대로 둔다 — 로그아웃했는데 토큰이 남는다.
     */
    public String clearCookie() {
        return cookie("", Duration.ZERO).toString();
    }

    /** 요청에 실려 온 refresh token. 없으면 null. */
    public String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 쿠키로 동작하는 경로(refresh·logout)의 요청 위조 방어 (계약서 §3).
     *
     * 쿠키는 브라우저가 <b>알아서</b> 붙인다. 그래서 다른 사이트가 사용자의 브라우저로
     * 우리 API 를 부르면 그 요청에도 쿠키가 실린다 — 방어가 없으면 남의 페이지가 사용자를
     * 로그아웃시키거나 토큰을 로테이션시킬 수 있다.
     *
     * 방어는 세 겹이다. {@code SameSite=Strict} 가 첫째고, {@code X-Kickoff-Client} 는
     * 단순 요청에 붙일 수 없는 헤더라 폼 전송으로는 이 경로에 닿지 못하는 것이 둘째다.
     * 이 검사가 셋째 — 앞의 둘이 브라우저 구현에 기대는 반면 이쪽은 <b>서버가 직접</b> 본다.
     *
     * <b>허용 목록이 비어 있으면 검사하지 않는다.</b> 그 상태의 뜻은 "전부 허용"이라
     * 판정 기준이 없다. 개발에서 Origin 이 제각각(localhost 포트·exp://)인 것도 이유다.
     */
    public void requireTrustedOrigin(HttpServletRequest request) {
        if (!allowedOrigins.isConfigured()) {
            return;
        }
        if (!allowedOrigins.matches(request.getHeader(HttpHeaders.ORIGIN))) {
            // Origin 값을 로그에 남기지 않는다 — 공격자가 고른 문자열이 그대로 로그에 쌓인다.
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge);
        if (properties.domain() != null) {
            builder.domain(properties.domain());
        }
        return builder.build();
    }
}
