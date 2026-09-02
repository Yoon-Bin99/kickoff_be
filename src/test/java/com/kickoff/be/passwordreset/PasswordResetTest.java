package com.kickoff.be.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 비밀번호 재설정 (계약서 §3-3, v1.23.0).
 *
 * <b>이 기능의 규칙은 거의 전부 "계정 존재를 노출하지 않는 것"이다.</b> 그래서 테스트도
 * 대부분 <b>두 경우가 같은지</b>를 본다 — 있는 계정과 없는 계정이 같은 응답, 같은 한도,
 * 같은 실패 코드를 내는지.
 *
 * 그중 가장 조용히 새는 자리가 <b>레이트리밋</b>이다. 한도를 "발송된 코드" 기준으로 세면
 * 계정 없는 이메일은 영영 한도에 안 걸리고, 그러면 429 가 거꾸로 "이 계정은 없다"는
 * 신호가 된다. 응답 코드도 문구도 다 맞는데 존재가 새어 나간다.
 */
class PasswordResetTest extends IntegrationTestSupport {

    private static final String KNOWN = "known@example.com";
    private static final String UNKNOWN = "nobody@example.com";

    @Autowired
    private com.kickoff.be.passwordreset.repository.PasswordResetCodeRepository resetRepository;

    private User user;

    @BeforeEach
    void setUpAccount() {
        user = createUser(KNOWN, "김주장", "010-1111-1111");
        emailClient.reset();
    }

    // ── 존재 비노출

    @Test
    @DisplayName("있는 계정도 없는 계정도 204 — 응답으로는 구별되지 않는다")
    void alwaysNoContent() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        request(UNKNOWN).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("소셜 전용 계정도 204 — 비밀번호가 없을 뿐 존재는 숨긴다")
    void socialAccountAlsoNoContent() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜사용자");
        social.updatePassword(null);
        userRepository.save(social);

        request("social@example.com").andExpect(status().isNoContent());
        assertThat(emailClient.sent()).isEmpty();
    }

    @Test
    @DisplayName("없는 이메일도 한도에 걸린다 — 여기가 새면 429 가 존재 신호가 된다")
    void rateLimitAppliesToUnknownEmail() throws Exception {
        // 한도를 "발송된 코드" 기준으로 세면 이 테스트가 깨진다. 없는 계정은 코드를
        // 안 만드니 영영 한도에 안 걸리고, 그 차이가 곧 계정 존재 여부다.
        request(UNKNOWN).andExpect(status().isNoContent());
        request(UNKNOWN)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RATE_LIMITED"));
    }

    @Test
    @DisplayName("있는 이메일도 같은 한도 — 두 경우의 응답이 같다")
    void rateLimitAppliesToKnownEmail() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        request(KNOWN).andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("대소문자만 바꿔 한도를 우회할 수 없다")
    void rateLimitIgnoresCase() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        request("KNOWN@Example.COM").andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("확인 실패는 만료·코드없음·계정없음이 전부 같은 응답")
    void confirmFailuresLookIdentical() throws Exception {
        // 코드를 받은 적이 없는 계정
        confirm(KNOWN, "123456", "newPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
        // 아예 없는 계정
        confirm(UNKNOWN, "123456", "newPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("코드를 받은 뒤 탈퇴해도 같은 EXPIRED — 여기서 갈리면 존재가 샌다")
    void deletedAfterIssueStillLooksExpired() throws Exception {
        // 좁지만 실재하는 창이다. 코드는 유효한데 계정이 사라진 상태 — 여기서
        // USER_NOT_FOUND 를 주면 "그 이메일에 계정이 있었다"가 확정된다.
        request(KNOWN).andExpect(status().isNoContent());
        String code = emailClient.lastCode();

        mockMvc.perform(delete("/api/users/me")
                        .header(org.springframework.http.HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        confirm(KNOWN, code, "newPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    // ── 코드 발송과 확인

    @Test
    @DisplayName("있는 계정에는 6자리 코드가 담긴 메일이 나간다")
    void sendsCodeToKnownAccount() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());

        assertThat(emailClient.sent()).hasSize(1);
        assertThat(emailClient.last().to()).isEqualTo(KNOWN);
        assertThat(emailClient.last().subject()).isEqualTo("[킥오프] 비밀번호 재설정 인증번호");
        assertThat(emailClient.lastCode()).hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("코드가 맞으면 비밀번호가 바뀌고 새 비밀번호로 로그인된다")
    void confirmChangesPassword() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        String code = emailClient.lastCode();

        confirm(KNOWN, code, "newPass1!").andExpect(status().isNoContent());

        login(KNOWN, "newPass1!").andExpect(status().isOk());
        login(KNOWN, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("성공하면 refresh token 이 폐기된다 — 탈취 대응의 핵심")
    void confirmRevokesRefreshTokens() throws Exception {
        // 남의 기기에 살아 있는 refresh 가 그대로면 비밀번호를 바꿔도 계정을 못 되찾는다
        String refresh = refreshTokenOf(login(KNOWN, PASSWORD));
        request(KNOWN).andExpect(status().isNoContent());
        confirm(KNOWN, emailClient.lastCode(), "newPass1!").andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("틀린 코드는 400 MISMATCH, 5회 넘으면 만료로 바뀐다")
    void wrongCodeThenLockout() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());

        for (int i = 1; i <= 5; i++) {
            confirm(KNOWN, "000000", "newPass1!")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        }
        // 6번째부터는 코드가 무효다 — 맞는 코드를 넣어도 안 통한다
        confirm(KNOWN, emailClient.lastCode(), "newPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("같은 코드로 두 번 바꿀 수 없다")
    void codeIsSingleUse() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        String code = emailClient.lastCode();

        confirm(KNOWN, code, "newPass1!").andExpect(status().isNoContent());
        confirm(KNOWN, code, "other1234!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("새 비밀번호는 signup 과 같은 규칙")
    void newPasswordFollowsSignupRules() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        String code = emailClient.lastCode();

        confirm(KNOWN, code, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        confirm(KNOWN, code, "nospecial123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("이메일 형식 오류만 400 — 계정 없음은 오류가 아니다")
    void onlyMalformedEmailIs400() throws Exception {
        request("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("재요청하면 이전 코드가 무효가 된다")
    void reissueInvalidatesPreviousCode() throws Exception {
        request(KNOWN).andExpect(status().isNoContent());
        String first = emailClient.lastCode();
        // 한도(1분 1회)를 피하려고 첫 요청 시각을 과거로 민다
        resetRepository.findAll().forEach(row -> shiftCreatedAt(row.getId()));

        request(KNOWN).andExpect(status().isNoContent());
        String second = emailClient.lastCode();

        confirm(KNOWN, first, "newPass1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        confirm(KNOWN, second, "newPass1!").andExpect(status().isNoContent());
    }

    // ── 헬퍼

    private ResultActions request(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions confirm(String email, String code, String newPassword)
            throws Exception {
        return mockMvc.perform(post("/api/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code
                        + "\",\"newPassword\":\"" + newPassword + "\"}"));
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

    /** 한도 창을 벗어나게 만든다 — 테스트가 1분을 실제로 기다릴 수는 없다. */
    private void shiftCreatedAt(Long id) {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createQuery(
                                "update PasswordResetCode c set c.createdAt = :past "
                                        + "where c.id = :id")
                        .setParameter("past", java.time.OffsetDateTime.now().minusMinutes(5))
                        .setParameter("id", id)
                        .executeUpdate());
    }
}
