package com.kickoff.be.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.support.IntegrationTestSupport;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * refresh token 과 자동 로그인 (계약서 §3, v1.7.0).
 *
 * 핵심은 <b>로테이션</b>이다 — refresh 를 한 번 쓰면 그 자리에서 무효가 되고 새 것이 나온다.
 * 그래서 "성공한다"보다 <b>"쓰고 난 뒤에는 못 쓴다"</b>가 더 중요한 단언이다. 무효화가 안
 * 되면 유출된 토큰을 30일 동안 아무도 막을 수 없다.
 *
 * 소셜 경로의 refresh 발급은 SocialLoginTest 가 함께 본다.
 */
class RefreshTokenTest extends IntegrationTestSupport {

    private static final String EMAIL = "kim@example.com";
    private static final String PASSWORD = "pass1234";

    @BeforeEach
    void setUpUser() throws Exception {
        signup(EMAIL).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("가입·로그인 응답이 토큰 쌍을 함께 준다")
    void loginReturnsBothTokens() throws Exception {
        signup("another@example.com")
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.id").isNotEmpty());

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("refresh 로 새 쌍을 받는다 — user 는 없다")
    void refreshReturnsNewPair() throws Exception {
        String refreshToken = refreshTokenOf(login());

        refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                // 계약서 §3: refresh 응답에는 user 가 없다. 필요하면 /auth/me 로 받는다
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    @DisplayName("새로 받은 access 로 인증 요청이 통한다")
    void newAccessTokenWorks() throws Exception {
        String newAccess = accessTokenOf(refresh(refreshTokenOf(login())));

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));
    }

    @Test
    @DisplayName("로테이션 — 한 번 쓴 refresh 는 곧바로 401 이다")
    void rotatedTokenIsRejected() throws Exception {
        String first = refreshTokenOf(login());
        String second = refreshTokenOf(refresh(first));

        assertThat(second).isNotEqualTo(first);

        refresh(first)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 새 것은 살아 있어야 한다 — 무효화가 과하게 번지면 로그인이 끊긴다
        refresh(second).andExpect(status().isOk());
    }

    @Test
    @DisplayName("만료된 refresh 는 401 — 해시가 맞아도 시각을 따로 본다")
    void expiredTokenIsRejected() throws Exception {
        String refreshToken = refreshTokenOf(login());
        expireRefreshToken();

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃하면 refresh 가 폐기된다 — 이어서 401")
    void logoutRevokesRefreshToken() throws Exception {
        ResultActions loggedIn = login();
        String accessToken = accessTokenOf(loggedIn);
        String refreshToken = refreshTokenOf(loggedIn);

        logout(accessToken).andExpect(status().isNoContent());

        refresh(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃은 멱등 — 두 번 불러도 204")
    void logoutIsIdempotent() throws Exception {
        String accessToken = accessTokenOf(login());

        logout(accessToken).andExpect(status().isNoContent());
        logout(accessToken).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("새 로그인이 이전 기기의 refresh 를 무효화한다 — 단일 기기 정책")
    void newLoginInvalidatesPreviousRefreshToken() throws Exception {
        String firstDevice = refreshTokenOf(login());
        String secondDevice = refreshTokenOf(login());

        refresh(firstDevice)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        refresh(secondDevice).andExpect(status().isOk());
    }

    @Test
    @DisplayName("아무 문자열이나 보내면 401, 빈 값이면 400")
    void garbageTokenIsRejected() throws Exception {
        refresh("이런-토큰은-없다")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 형식 자체가 틀린 건 검증 단계에서 걸린다
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("refresh 는 access 없이 부른다 — 만료된 access 로 부르는 게 정상 흐름이다")
    void refreshNeedsNoAccessToken() throws Exception {
        String refreshToken = refreshTokenOf(login());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + refreshToken + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인은 로그아웃할 수 없다 — 401")
    void anonymousCannotLogout() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("서버는 refresh 원문을 저장하지 않는다 — 해시만 남는다")
    void serverStoresOnlyTheHash() throws Exception {
        String refreshToken = refreshTokenOf(login());

        User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getRefreshTokenHash())
                .isNotNull()
                .isNotEqualTo(refreshToken)
                .hasSize(64);
        assertThat(stored.getRefreshTokenExpiresAt()).isAfter(OffsetDateTime.now());
    }

    /** 만료를 흉내낸다. 저장된 해시는 그대로 두고 시각만 과거로 민다. */
    private void expireRefreshToken() {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        user.issueRefreshToken(user.getRefreshTokenHash(), OffsetDateTime.now().minusMinutes(1));
        userRepository.saveAndFlush(user);
    }

    private ResultActions signup(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\","
                        + " \"nickname\": \"김주장\", \"phone\": \"010-1111-1111\"}"));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + EMAIL + "\", \"password\": \"" + PASSWORD + "\"}"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"" + refreshToken + "\"}"));
    }

    private ResultActions logout(String accessToken) throws Exception {
        return mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private String refreshTokenOf(ResultActions actions) throws Exception {
        return fieldOf(actions, "refreshToken");
    }

    private String accessTokenOf(ResultActions actions) throws Exception {
        return fieldOf(actions, "accessToken");
    }

    private String fieldOf(ResultActions actions, String field) throws Exception {
        return JsonPath.read(bodyOf(actions), "$." + field);
    }
}
