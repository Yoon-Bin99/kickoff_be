package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 본인 확인 (계약서 §3-4 위, v1.24.1).
 *
 * <b>상태를 바꾸지 않는 것이 이 API 의 전부다.</b> 비밀번호를 다루는 다른 엔드포인트
 * (변경·재설정·탈퇴)는 전부 무언가를 바꾸는데, 여기만 묻고 끝난다. 실수로 뭔가를 바꾸면
 * 게이트를 통과한 사용자가 자기도 모르게 상태를 건드린 셈이 된다.
 *
 * 그리고 <b>비밀번호 변경과 같은 응답을 내야 한다.</b> 사용자에게는 "비밀번호를 다시
 * 대는" 같은 행동이라, 응답이 갈리면 화면이 이유 없이 달라진다.
 */
class PasswordVerifyTest extends IntegrationTestSupport {

    private User user;

    @BeforeEach
    void setUpUser() {
        user = createUser("member@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("맞으면 204")
    void correctPasswordIsNoContent() throws Exception {
        verify(user, PASSWORD).andExpect(status().isNoContent());
    }

    /**
     * 이 테스트는 <b>{@code readOnly = true} 와 짝</b>이다. 서비스가 readOnly 인 동안에는
     * 실수로 쓰기가 들어와도 플러시가 안 돼 반영되지 않으므로, 이 테스트만으로는 그 실수를
     * 못 잡는다. 대신 <b>readOnly 가 사라지는 순간</b> 이 테스트가 깬다 — 변이 검증으로
     * 확인했다. 구조와 테스트 둘 중 하나가 사라져도 나머지가 막는다.
     */
    @Test
    @DisplayName("아무것도 바뀌지 않는다 — 검증만 하는 API 다")
    void changesNothing() throws Exception {
        // 로그인해 refresh 를 만들어 둔다. 안 그러면 둘 다 null 이라 "안 바뀌었다"가
        // 아무것도 증명하지 못한다.
        login("member@example.com", PASSWORD).andExpect(status().isOk());
        User before = userRepository.findById(user.getId()).orElseThrow();
        String passwordBefore = before.getPassword();
        String refreshBefore = before.getRefreshTokenHash();
        assertThat(refreshBefore).as("전제: refresh 가 발급돼 있어야 한다").isNotNull();

        verify(user, PASSWORD).andExpect(status().isNoContent());
        verify(user, "wrong-pass1!").andExpect(status().isBadRequest());

        User after = userRepository.findById(user.getId()).orElseThrow();
        assertThat(after.getPassword()).isEqualTo(passwordBefore);
        // 세션도 그대로다 — 게이트를 통과했다고 로그인 상태가 달라질 이유가 없다
        assertThat(after.getRefreshTokenHash()).isEqualTo(refreshBefore);
        // 여전히 같은 비밀번호로 로그인된다
        login("member@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    @DisplayName("틀리면 400 PASSWORD_MISMATCH — 401 이 아니다")
    void wrongPasswordIs400() throws Exception {
        // 401 을 주면 FE 인터셉터가 세션 만료로 오인해 refresh 를 탄다. 계정 관리
        // 진입 게이트라 그 오작동이 특히 나쁘다 — 들어가려다 로그아웃된다.
        verify(user, "wrong-pass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("비밀번호 변경과 같은 응답을 낸다 — 사용자에게는 같은 행동이다")
    void matchesChangePasswordResponses() throws Exception {
        // 두 API 가 갈리면 화면이 이유 없이 달라진다. 서비스에서 같은 헬퍼를 쓰게
        // 해 뒀고, 여기서 그 약속을 못박는다.
        verify(user, "wrong-pass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me/password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-pass1!\","
                                + "\"newPassword\":\"newPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
    }

    @Test
    @DisplayName("옛 규칙 비밀번호도 통과한다 — 게이트가 계정을 가두면 안 된다")
    void legacyPasswordPasses() throws Exception {
        // v1.16.0 이전 규칙으로 만든 계정이 실재한다(시드가 그렇다). 여기서 형식을
        // 요구하면 그 사람들은 계정 관리에 들어갈 수조차 없다 — 비밀번호를 바꾸러
        // 가는 길이 비밀번호 규칙에 막히는 셈이다.
        User legacy = createUserWithPassword("legacy@example.com", "옛사용자",
                "010-2222-2222", "pass1234");

        verify(legacy, "pass1234").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("소셜 계정은 400 VALIDATION_FAILED — 물을 비밀번호가 없다")
    void socialAccountIsRejected() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜사용자");
        social.updatePassword(null);
        userRepository.save(social);

        verify(social, "anything1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("빈 비밀번호는 400 VALIDATION_FAILED")
    void blankPasswordIsRejected() throws Exception {
        verify(user, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("미인증은 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(post("/api/users/me/verify-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private ResultActions verify(User who, String password) throws Exception {
        return mockMvc.perform(post("/api/users/me/verify-password")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
