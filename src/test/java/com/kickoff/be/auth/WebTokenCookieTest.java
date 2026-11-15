package com.kickoff.be.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.auth.cookie.WebTokenDelivery;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 웹은 refresh token 을 HttpOnly 쿠키로 받는다 (계약서 §3 「웹의 토큰 전달」, v1.32.0).
 *
 * <b>이 파일이 지키는 것은 두 가지다.</b> 웹에서 refresh token 이 본문에 <b>실리지 않는
 * 것</b>(스크립트가 읽을 수 있는 곳에 30일짜리 토큰을 두지 않는다), 그리고 쿠키로 도는
 * 경로가 <b>다른 사이트에서 불릴 수 없는 것</b>(Origin 검사).
 *
 * 앱 경로가 그대로인지는 여기서 보지 않는다 — {@code AuthenticationTest}·
 * {@code RefreshTokenTest} 가 헤더 없이 돌고 있고, <b>그것들이 손대지 않은 채로 통과하는
 * 것</b>이 증거다.
 */
class WebTokenCookieTest extends IntegrationTestSupport {

    private static final String EMAIL = "web@example.com";
    private static final String WEB = WebTokenDelivery.WEB_CLIENT;
    private static final String COOKIE = WebTokenDelivery.COOKIE_NAME;

    // ── 토큰을 내주는 경로 (계약서 §3 의 표)

    @Test
    @DisplayName("웹 가입: 본문의 refreshToken 은 null 이고 쿠키로 온다")
    void signupPutsRefreshTokenInCookie() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andReturn();

        assertCookieLooksRight(result);
    }

    @Test
    @DisplayName("웹 로그인: 본문의 refreshToken 은 null 이고 쿠키로 온다")
    void loginPutsRefreshTokenInCookie() throws Exception {
        createUser(EMAIL, "김주장", "010-1111-1111");

        MvcResult result = webLogin().andReturn();

        assertCookieLooksRight(result);
    }

    /**
     * 쿠키 속성 하나하나가 방어다. HttpOnly 가 빠지면 스크립트가 읽을 수 있어 이 변경의
     * 이유 자체가 사라지고, Path 가 넓으면 일반 API 요청마다 쿠키가 따라붙어 요청 위조가
     * 닿는 표면이 커진다.
     */
    @Test
    @DisplayName("쿠키 속성: HttpOnly·SameSite·Path·Max-Age")
    void cookieCarriesEveryAttribute() throws Exception {
        createUser(EMAIL, "김주장", "010-1111-1111");

        webLogin()
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().path(COOKIE, "/api/auth"))
                // 30일 (jwt.refresh-expiration)
                .andExpect(cookie().maxAge(COOKIE, 30 * 24 * 60 * 60))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("SameSite=Strict")));
    }

    @Test
    @DisplayName("소셜 코드 교환도 같은 규칙을 탄다")
    void oauthExchangePutsRefreshTokenInCookie() throws Exception {
        String code = issueOAuthCode();

        MvcResult result = mockMvc.perform(post("/api/auth/oauth/exchange")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        assertCookieLooksRight(result);
    }

    // ── refresh (계약서 §3 — 본문을 읽지 않는다)

    @Test
    @DisplayName("웹 refresh: 쿠키로 돌고 새 쿠키를 받는다")
    void refreshRotatesTheCookie() throws Exception {
        createUser(EMAIL, "김주장", "010-1111-1111");
        String first = cookieValueOf(webLogin().andReturn());

        MvcResult result = mockMvc.perform(webRefresh().cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String rotated = cookieValueOf(result);
        assertThat(rotated).isNotBlank().isNotEqualTo(first);

        // 로테이션은 옛 토큰을 즉시 무효로 만든다 — 쿠키로도 같아야 한다
        mockMvc.perform(webRefresh().cookie(new Cookie(COOKIE, first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("쿠키가 없으면 401 이고 쿠키를 지운다")
    void refreshWithoutCookieIsUnauthorized() throws Exception {
        mockMvc.perform(webRefresh())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(cookie().maxAge(COOKIE, 0));
    }

    /**
     * 쓸 수 없는 쿠키는 지워 준다. 남겨 두면 브라우저가 매 시도마다 같은 죽은 토큰을 다시
     * 보내고, 사용자는 이유 없이 한 번 더 막힌다.
     */
    @Test
    @DisplayName("무효한 쿠키도 401 이고 쿠키를 지운다")
    void refreshWithGarbageCookieIsUnauthorized() throws Exception {
        mockMvc.perform(webRefresh().cookie(new Cookie(COOKIE, "이런-토큰은-없다")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andExpect(cookie().maxAge(COOKIE, 0));
    }

    /**
     * <b>본문으로는 통하지 않아야 한다.</b> 둘 중 하나를 고르게 두면, 공격자가 쿠키 대신
     * 본문에 토큰을 실어 Origin 검사를 비켜 갈 길이 생긴다.
     */
    @Test
    @DisplayName("웹에서는 본문의 refreshToken 을 쳐다보지 않는다")
    void refreshIgnoresBodyTokenForWeb() throws Exception {
        createUser(EMAIL, "김주장", "010-1111-1111");
        String valid = cookieValueOf(webLogin().andReturn());

        mockMvc.perform(webRefresh()
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(valid)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    /** 앱이 쿠키를 들고 와도 앱 규칙이다 — 헤더가 없으면 본문이 유일한 자격 증명이다. */
    @Test
    @DisplayName("앱 요청에 쿠키가 실려 와도 무시한다")
    void appRequestIgnoresCookie() throws Exception {
        createUser(EMAIL, "김주장", "010-1111-1111");
        String valid = cookieValueOf(webLogin().andReturn());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie(COOKIE, valid))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── logout

    @Test
    @DisplayName("웹 로그아웃은 쿠키를 지운다")
    void logoutClearsTheCookie() throws Exception {
        User user = createUser(EMAIL, "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/auth/logout")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0))
                .andExpect(cookie().path(COOKIE, "/api/auth"));
    }

    /** 앱 로그아웃은 쿠키를 건드리지 않는다 — 응답에 Set-Cookie 자체가 없어야 한다. */
    @Test
    @DisplayName("앱 로그아웃 응답에는 Set-Cookie 가 없다")
    void appLogoutSetsNoCookie() throws Exception {
        User user = createUser(EMAIL, "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    // ── 거들기

    private ResultActions webLogin() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    private MockHttpServletRequestBuilder webRefresh() {
        return post("/api/auth/refresh").header(WebTokenDelivery.CLIENT_HEADER, WEB);
    }

    private String signupBody() {
        return """
                {"email":"%s","password":"%s","nickname":"김주장","phone":"010-1111-1111","termsAgreed":true}
                """.formatted(EMAIL, PASSWORD);
    }

    /** 웹 복귀는 토큰이 아니라 일회용 코드를 싣는다 (§3-1, v1.26.0) — 그 코드를 받아 온다. */
    private String issueOAuthCode() throws Exception {
        kakaoStub.willReturn("kakao-web-cookie", "웹사용자");

        mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                        .param("redirect", "http://localhost:3000/oauth"))
                .andExpect(status().isFound());
        String state = kakaoStub.lastState();

        String location = mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "code-" + state.hashCode())
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        String code = UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap().get("code");
        assertThat(code).isNotBlank();
        return code;
    }

    private String cookieValueOf(MvcResult result) {
        jakarta.servlet.http.Cookie found = result.getResponse().getCookie(COOKIE);
        assertThat(found).as("refresh 쿠키가 있어야 한다").isNotNull();
        return found.getValue();
    }

    private void assertCookieLooksRight(MvcResult result) throws Exception {
        jakarta.servlet.http.Cookie found = result.getResponse().getCookie(COOKIE);
        assertThat(found).as("refresh 쿠키가 있어야 한다").isNotNull();
        assertThat(found.getValue()).isNotBlank();
        assertThat(found.isHttpOnly()).isTrue();
        assertThat(found.getPath()).isEqualTo("/api/auth");

        // 본문 어디에도 그 값이 없어야 한다 — 쿠키로 옮긴 뜻이 그것이다
        String body = result.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain(found.getValue());
        assertThat((Object) JsonPath.read(body, "$.accessToken")).isNotNull();
    }
}
