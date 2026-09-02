package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 로그인 상태의 비밀번호 변경 (계약서 §3-3 아래, v1.24.0).
 *
 * 재설정(§3-3)과 <b>정반대인 규칙이 하나 있다: 세션을 끊지 않는다.</b> 재설정은 탈취
 * 대응이라 남의 기기에 살아 있는 세션을 끊는 게 목적이지만, 이쪽은 본인이 방금 현재
 * 비밀번호를 댄 일상 변경이다. 여기서 폐기하면 <b>비밀번호를 바꿨다는 이유로 자기 앱에서
 * 튕겨 나간다</b> — 200 도 400 도 아니고 그냥 로그인 화면으로 돌아가는데, 사용자는 변경이
 * 실패한 줄 안다.
 *
 * 두 경로가 같은 파일에 있지 않아서 더 그렇다. 한쪽을 고치는 사람이 다른 쪽 규칙을
 * 모르고 "일관성"을 맞추려 들 수 있다.
 */
class PasswordChangeTest extends IntegrationTestSupport {

    private static final String NEW_PASSWORD = "newPass1!";

    private User user;

    @BeforeEach
    void setUpUser() {
        user = createUser("member@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("바꾸면 새 비밀번호로 로그인되고 옛 것은 막힌다")
    void changesPassword() throws Exception {
        change(user, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        login("member@example.com", NEW_PASSWORD).andExpect(status().isOk());
        login("member@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 세션은 유지된다 — 재설정과 반대다")
    void keepsCurrentSession() throws Exception {
        String refresh = refreshTokenOf(login("member@example.com", PASSWORD));

        change(user, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // 여기서 폐기하면 비밀번호를 바꿨다는 이유로 자기 앱에서 튕겨 나간다.
        // §3-3 재설정은 정반대로 전부 끊는다 — 그쪽은 탈취 대응이라서다.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 400 PASSWORD_MISMATCH — 401 이 아니다")
    void wrongCurrentPasswordIs400() throws Exception {
        // 401 을 주면 FE 인터셉터가 세션 만료로 오인해 refresh 를 탄다. 탈퇴(§3-4)와
        // 같은 이유로 같은 코드를 쓴다.
        change(user, "wrong-pass1!", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        login("member@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("새 비밀번호는 signup 과 같은 규칙")
    void newPasswordFollowsSignupRules() throws Exception {
        change(user, PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        change(user, PASSWORD, "nospecial123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("현재 비밀번호에는 형식 규칙을 걸지 않는다 — 옛 규칙 계정이 갇히면 안 된다")
    void currentPasswordIsNotFormatChecked() throws Exception {
        // v1.16.0 이전 규칙으로 만들어진 계정이 실재한다(시드가 그렇다). 여기서 형식으로
        // 먼저 거절하면 그 사람들은 비밀번호를 바꿀 수도 없다 — 규칙을 지키려다 규칙을
        // 지킬 방법을 없애는 셈이다.
        User legacy = createUserWithPassword("legacy@example.com", "옛사용자",
                "010-2222-2222", "pass1234");

        change(legacy, "pass1234", NEW_PASSWORD).andExpect(status().isNoContent());
        login("legacy@example.com", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("소셜 계정은 400 — 바꿀 비밀번호가 없다")
    void socialAccountIsRejected() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜사용자");
        social.updatePassword(null);
        userRepository.save(social);

        change(social, "anything1!", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("미인증은 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"a\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private ResultActions change(User who, String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\""
                        + next + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private String refreshTokenOf(ResultActions actions) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
                actions.andReturn().getResponse().getContentAsString(), "$.refreshToken");
    }
}
