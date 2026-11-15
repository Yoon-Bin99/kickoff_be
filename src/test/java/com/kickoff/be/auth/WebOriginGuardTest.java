package com.kickoff.be.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.auth.cookie.WebTokenDelivery;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

/**
 * 쿠키로 도는 경로의 요청 위조 방어 (계약서 §3 「웹의 토큰 전달」, v1.32.0).
 *
 * <b>쿠키는 브라우저가 알아서 붙인다.</b> 그래서 다른 사이트가 사용자의 브라우저로 우리
 * API 를 부르면 그 요청에도 쿠키가 실린다 — 방어가 없으면 남의 페이지가 사용자를
 * 로그아웃시키거나 토큰을 로테이션시켜 <b>로그인 상태를 끊어 놓을</b> 수 있다.
 *
 * 허용 목록을 주고 띄운다. 기본 test 프로파일은 목록이 비어 있어(= 전부 허용) 검사가
 * 아예 돌지 않는데, 그 상태는 {@code WebOriginGuardDisabledTest} 가 따로 본다.
 */
@TestPropertySource(properties = "CORS_ALLOWED_ORIGINS=https://kickoff.example,http://localhost:*")
class WebOriginGuardTest extends IntegrationTestSupport {

    private static final String WEB = WebTokenDelivery.WEB_CLIENT;
    private static final String COOKIE = WebTokenDelivery.COOKIE_NAME;

    @Test
    @DisplayName("허용 목록에 있는 Origin 은 통과한다")
    void allowedOriginPasses() throws Exception {
        mockMvc.perform(webRefresh()
                        .header(HttpHeaders.ORIGIN, "https://kickoff.example")
                        .cookie(new Cookie(COOKIE, "없는-토큰")))
                // Origin 은 통과하고 토큰에서 막힌다 — 403 이 아니라 401 이어야 한다
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("와일드카드 패턴도 CORS 와 같은 규칙으로 통과한다")
    void wildcardPatternPasses() throws Exception {
        mockMvc.perform(webRefresh()
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .cookie(new Cookie(COOKIE, "없는-토큰")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * <b>여기는 CORS 필터가 먼저 답한다.</b> 허용 목록에 없는 Origin 이 붙은 실제 요청은
     * 스프링의 CORS 필터가 컨트롤러에 닿기 전에 403 으로 끊는다 — 그래서 본문이 없다
     * (계약서 §0 의 오류 모양이 아니다). 상태는 계약이 정한 403 그대로다.
     *
     * 그 사실을 알고 두는 것이 중요하다. 우리 가드가 실제로 유일한 방어가 되는 자리는
     * <b>아래의 "Origin 이 없는 요청"</b>이다 — CORS 필터는 Origin 이 없으면 교차 출처가
     * 아니라고 보고 그냥 통과시킨다.
     */
    @Test
    @DisplayName("다른 사이트의 Origin 은 403 이다 — CORS 필터가 먼저 끊는다")
    void foreignOriginIsForbidden() throws Exception {
        mockMvc.perform(webRefresh()
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(COOKIE, "아무거나")))
                .andExpect(status().isForbidden());
    }

    /**
     * <b>이 가드가 혼자 막는 자리다.</b> CORS 필터는 Origin 이 없는 요청을 교차 출처로
     * 보지 않아 그냥 통과시킨다.
     *
     * 그리고 401 이 아니라 403 이어야 한다. 401 이면 FE 는 "로그인이 끊겼다"로 읽고
     * 사용자를 로그인 화면으로 보낸다 — 실제로는 설정이나 공격의 문제인데 사용자가 쫓겨난다.
     */
    @Test
    @DisplayName("Origin 이 아예 없으면 403 이다")
    void missingOriginIsForbidden() throws Exception {
        mockMvc.perform(webRefresh().cookie(new Cookie(COOKIE, "아무거나")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("로그아웃도 같은 검사를 탄다")
    void logoutIsGuardedToo() throws Exception {
        User user = createUser("web@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/auth/logout")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/logout")
                        .header(WebTokenDelivery.CLIENT_HEADER, WEB)
                        .header(HttpHeaders.ORIGIN, "https://kickoff.example")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent());
    }

    /**
     * <b>앱은 이 검사를 타지 않는다.</b> 네이티브는 Origin 을 보내지 않으므로, 앱까지
     * 검사하면 허용 목록을 넣는 순간 <b>앱 로그인이 통째로 막힌다.</b> 쿠키를 쓰지 않는
     * 클라이언트라 요청 위조의 대상도 아니다.
     */
    @Test
    @DisplayName("앱 요청은 Origin 이 없어도 그대로 돈다")
    void appRequestsAreNotGuarded() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"없는-토큰\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webRefresh() {
        return post("/api/auth/refresh").header(WebTokenDelivery.CLIENT_HEADER, WEB);
    }
}
