package com.kickoff.be.verification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.oauth.entity.SocialAccount;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 인증 확인 응답의 existingAccount (계약서 §3-2, v1.17.0).
 *
 * 이 값이 붙는 <b>자리</b>가 곧 보안 설계다. 확인 응답에만 실리므로, 남의 번호로 가입
 * 여부와 가입 수단을 캐려면 그 번호로 온 문자를 읽어야 한다. 발송 응답이나 availability
 * 로 내려가는 순간 그 성질이 사라지는데, 기능은 멀쩡히 동작해서 화면만 봐서는 모른다.
 */
class ExistingAccountTest extends IntegrationTestSupport {

    private static final String PHONE = "010-7000-1234";

    @Test
    @DisplayName("이메일 계정이면 EMAIL 과 마스킹된 이메일을 준다")
    void emailAccount() throws Exception {
        createUser("kim@example.com", "김주장", PHONE);

        confirmFor(PHONE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").isNotEmpty())
                .andExpect(jsonPath("$.existingAccount.method").value("EMAIL"))
                .andExpect(jsonPath("$.existingAccount.maskedEmail").value("ki***@ex*****.com"));
    }

    @Test
    @DisplayName("소셜로만 가입한 계정이면 제공자를 주고 이메일은 없다")
    void socialAccount() throws Exception {
        User user = createUserWithoutEmail("카카오사람", PHONE);
        socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(AuthProvider.KAKAO)
                .providerUserId("kakao-7000")
                .build());

        confirmFor(PHONE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.existingAccount.method").value("KAKAO"))
                // 이메일이 없는데 EMAIL 이라고 답하면 사용자가 오지 않을 메일함을 뒤진다
                .andExpect(jsonPath("$.existingAccount.maskedEmail").doesNotExist());
    }

    @Test
    @DisplayName("계정이 없으면 null — 키는 그대로 있다")
    void noAccount() throws Exception {
        confirmFor(PHONE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").isNotEmpty())
                // 키가 빠지면 FE 가 "아직 안 온 것"과 "없는 것"을 구분하지 못한다
                .andExpect(jsonPath("$.existingAccount").doesNotExist());
    }

    @Test
    @DisplayName("마스킹 경계 — 2자 미만은 첫 자만 남는다")
    void maskingBoundaries() throws Exception {
        createUser("a@b.com", "짧은사람", PHONE);

        confirmFor(PHONE)
                // 로컬파트는 길이와 무관하게 *** 가 붙고, 도메인은 남은 글자 수만큼이다.
                // 2자짜리를 한 자로 깎으면 계약과 다른데 눈에는 잘 안 띈다
                .andExpect(jsonPath("$.existingAccount.maskedEmail").value("a***@b.com"));
    }

    @Test
    @DisplayName("발송 응답에는 실리지 않는다 — 코드를 모르는 사람은 알 수 없어야 한다")
    void notExposedBeforeVerification() throws Exception {
        createUser("secret@example.com", "비밀", PHONE);

        // 발송은 본문이 없는 204 다. 여기에 실리면 남의 번호를 넣는 것만으로 가입 여부와
        // 수단이 새어 나간다 — 이 API 는 인증이 필요 없다
        send(PHONE).andExpect(status().isNoContent())
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString()).isEmpty());
    }

    @Test
    @DisplayName("availability 의 phone 은 여전히 가능 여부만 답한다")
    void availabilityStillHidesTheMethod() throws Exception {
        createUser("hidden@example.com", "숨김", PHONE);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/auth/availability").param("phone", PHONE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(false))
                // 수단까지 알려주면 인증 없이 캐낼 수 있다
                .andExpect(jsonPath("$.method").doesNotExist())
                .andExpect(jsonPath("$.maskedEmail").doesNotExist())
                .andExpect(jsonPath("$.existingAccount").doesNotExist());
    }

    private ResultActions send(String phone) throws Exception {
        return mockMvc.perform(post("/api/auth/phone/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\"}"));
    }

    private ResultActions confirmFor(String phone) throws Exception {
        send(phone).andExpect(status().isNoContent());
        return mockMvc.perform(post("/api/auth/phone/verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"%s\", \"code\": \"%s\"}"
                        .formatted(phone, smsClient.lastCode())));
    }
}
