package com.kickoff.be.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 로그인 실패 반복 차단 (계약서 §0 {@code LOGIN_RATE_LIMITED}, v1.25.1).
 *
 * 문자·메일·AI·재설정에는 전부 한도가 있었는데 로그인만 없어서, 비밀번호를 무제한으로
 * 찍어 볼 수 있었다. 이메일 존재 여부는 {@code availability} 로 확인되므로 공격자는
 * "있는 계정"만 골라 시도할 수 있다 — 그게 이 한도가 막으려는 그림이다.
 *
 * <b>가장 중요한 단언은 "성공하면 풀린다"</b>이다. 그게 없으면 비밀번호를 몇 번 틀린
 * 사용자가 맞는 비밀번호를 대고도 15분을 기다리게 된다 — 공격을 막으려다 본인을 막는다.
 */
class LoginRateLimitTest extends IntegrationTestSupport {

    private static final String EMAIL = "member@example.com";

    @BeforeEach
    void setUpAccount() {
        createUser(EMAIL, "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("실패 10회까지는 401 이고, 11번째부터 429")
    void blocksAfterTenFailures() throws Exception {
        for (int i = 1; i <= 10; i++) {
            login(EMAIL, "wrong-pass1!")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
        }

        login(EMAIL, "wrong-pass1!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    /**
     * 차단 중에는 <b>맞는 비밀번호도</b> 막힌다. 안 그러면 한도가 무의미하다 —
     * 공격자는 어차피 맞는 비밀번호를 찾으려고 시도하는 중이다.
     */
    @Test
    @DisplayName("차단 중에는 올바른 비밀번호도 429")
    void blocksEvenCorrectPasswordWhileLimited() throws Exception {
        for (int i = 1; i <= 10; i++) {
            login(EMAIL, "wrong-pass1!").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    @Test
    @DisplayName("성공하면 카운터가 풀린다 — 다시 열 번 틀릴 수 있다")
    void successResetsCounter() throws Exception {
        for (int i = 1; i <= 9; i++) {
            login(EMAIL, "wrong-pass1!").andExpect(status().isUnauthorized());
        }

        login(EMAIL, PASSWORD).andExpect(status().isOk());

        // 풀리지 않았다면 여기서 429 가 난다.
        for (int i = 1; i <= 10; i++) {
            login(EMAIL, "wrong-pass1!").andExpect(status().isUnauthorized());
        }
    }

    /**
     * 계정별로 센다. 한 계정이 막혔다고 다른 사람이 못 들어오면 그건 한도가 아니라
     * 전면 차단이고, 남의 계정을 일부러 막아 두는 괴롭힘 경로가 된다.
     */
    @Test
    @DisplayName("다른 이메일은 영향을 받지 않는다")
    void otherAccountIsUnaffected() throws Exception {
        createUser("other@example.com", "이감독", "010-2222-2222");
        for (int i = 1; i <= 10; i++) {
            login(EMAIL, "wrong-pass1!").andExpect(status().isUnauthorized());
        }

        login("other@example.com", PASSWORD).andExpect(status().isOk());
    }

    /**
     * 대소문자만 바꿔 부르면 한도가 두 배가 되면 안 된다. 로그인 자체가 대소문자를
     * 무시해 계정을 찾으므로(§3, v1.16.0), 카운터도 같은 기준으로 묶여야 한다.
     */
    @Test
    @DisplayName("대소문자만 다른 주소는 같은 카운터로 묶인다")
    void caseVariantsShareTheCounter() throws Exception {
        for (int i = 1; i <= 10; i++) {
            login(EMAIL, "wrong-pass1!").andExpect(status().isUnauthorized());
        }

        login("MEMBER@EXAMPLE.COM", "wrong-pass1!")
                .andExpect(status().isTooManyRequests());
    }

    /**
     * 없는 계정에 대한 시도도 세야 한다. 안 세면 "한도에 걸리는가"로 계정 존재 여부를
     * 물어볼 수 있고, 존재하는 계정만 골라 무제한으로 시도할 수 있게 된다.
     */
    @Test
    @DisplayName("없는 계정으로 시도해도 한도에 걸린다 — 429 가 존재 신호가 되지 않게")
    void unknownAccountIsAlsoCounted() throws Exception {
        for (int i = 1; i <= 10; i++) {
            login("nobody@example.com", "wrong-pass1!")
                    .andExpect(status().isUnauthorized());
        }

        login("nobody@example.com", "wrong-pass1!")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
