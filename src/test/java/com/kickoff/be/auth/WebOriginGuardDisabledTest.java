package com.kickoff.be.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.auth.cookie.WebTokenDelivery;
import com.kickoff.be.support.IntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 허용 목록이 비어 있으면 Origin 검사를 하지 않는다 (계약서 §3, v1.32.0).
 *
 * <b>판정 기준이 없기 때문이다.</b> 빈 목록의 뜻은 "전부 허용"이라, 거기서 Origin 을
 * 본다면 무엇과 비교해야 할지가 정해지지 않는다. 개발에서 Origin 이 제각각인 것도
 * 이유다 — localhost 포트가 매번 다르고 Expo 는 {@code exp://} 로 온다.
 *
 * 이 테스트가 없으면 "검사를 넣었더니 개발에서 웹이 안 돌더라"가 조용히 생긴다.
 * (허용 목록이 있을 때의 동작은 {@code WebOriginGuardTest} 가 본다.)
 */
class WebOriginGuardDisabledTest extends IntegrationTestSupport {

    @Test
    @DisplayName("목록이 비면 남의 Origin 이어도 403 이 아니다")
    void foreignOriginPassesWhenListIsEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(WebTokenDelivery.CLIENT_HEADER, WebTokenDelivery.WEB_CLIENT)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(WebTokenDelivery.COOKIE_NAME, "없는-토큰")))
                // Origin 에서 막히지 않고 토큰 검사까지 간다
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("목록이 비면 Origin 이 없어도 그대로 돈다")
    void missingOriginPassesWhenListIsEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(WebTokenDelivery.CLIENT_HEADER, WebTokenDelivery.WEB_CLIENT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }
}
