package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 가입할 때 받는 동의 (계약서 §3 「동의」, v1.34.1).
 *
 * <b>항목을 따로 받는 것이 이 구조의 전부다</b>(개인정보 보호법 제22조 제1항). 한 번의
 * 체크로 묶으면 무엇에 동의했는지가 기록으로 남지 않는다.
 *
 * 연락처 제공 동의는 여기 없다 — 팀을 만들 때 받는다({@code ConsentTeamOwnerTest}).
 */
class ConsentSignupTest extends IntegrationTestSupport {

    @Test
    @DisplayName("세 항목을 모두 동의하면 가입되고 항목별로 기록된다")
    void signupRecordsEachItem() throws Exception {
        signup(consents(true, true, true)).andExpect(status().isCreated());

        User user = userRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(consentRepository.findByUserId(user.getId()))
                .extracting(c -> c.getConsentType())
                .containsExactlyInAnyOrder(ConsentType.AGE14, ConsentType.TERMS,
                        ConsentType.PRIVACY);
    }

    /**
     * 그때 시행 중이던 문서의 시행일을 함께 남긴다. <b>"동의했다"만으로는 무엇에 동의했는지
     * 증명할 수 없다</b> — 약관이 개정되면 같은 시각의 동의가 다른 문서를 가리킨다.
     */
    @Test
    @DisplayName("기록에 그때의 시행일이 함께 남는다")
    void signupRecordsDocumentEffectiveAt() throws Exception {
        signup(consents(true, true, true)).andExpect(status().isCreated());

        User user = userRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(consentRepository.findByUserId(user.getId()))
                .allSatisfy(consent -> {
                    assertThat(consent.getAgreedAt()).isNotNull();
                    assertThat(consent.getDocumentEffectiveAt()).isNotNull();
                });
    }

    @Test
    @DisplayName("가입 직후에는 동의 화면이 필요 없다")
    void freshSignupNeedsNoConsentScreen() throws Exception {
        signup(consents(true, true, true))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.consentRequired").value(false))
                .andExpect(jsonPath("$.user.pendingConsents").isEmpty())
                .andExpect(jsonPath("$.user.termsAgreedAt").isNotEmpty());
    }

    /**
     * <b>항목별로 알려 줘야 한다.</b> FE 동의 화면은 체크박스가 셋이고, 어느 칸을 가리켜야
     * 할지 모르면 "동의해 주세요"만 띄우고 사용자는 무엇이 빠졌는지 찾아야 한다.
     */
    @Test
    @DisplayName("동의하지 않은 항목이 fieldErrors 에 항목별로 담긴다")
    void rejectedItemIsNamed() throws Exception {
        signup(consents(true, false, true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.terms')]").exists());
    }

    @Test
    @DisplayName("세 항목이 다 빠지면 셋 다 보고된다 — 한 번에 고치게")
    void allMissingItemsAreReported() throws Exception {
        signup("{\"age14\":false,\"terms\":false,\"privacy\":false}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.age14')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.terms')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.privacy')]").exists());
    }

    /**
     * 누락과 거부를 가린다. 누락은 <b>구버전 앱</b>이 보내는 모양이라 "업데이트해 주세요"고,
     * 거부는 "동의해야 가입할 수 있습니다"다 — 둘 다 400 이지만 사용자가 할 일이 다르다.
     */
    @Test
    @DisplayName("항목을 아예 안 보내면 안내가 다르다 — 업데이트 안내")
    void missingItemAsksForUpdate() throws Exception {
        signup("{\"terms\":true,\"privacy\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.age14')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("업데이트"))));
    }

    @Test
    @DisplayName("consents 자체가 없으면 400 이다 — 조용히 통과하지 않는다")
    void missingConsentsObjectIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"pass1234!",
                                 "nickname":"김주장","phone":"010-1234-5678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents')]").exists());
    }

    /** 옛 필드는 읽지 않는다 (계약서 §3 — "보내도 읽지 않는다"). */
    @Test
    @DisplayName("옛 termsAgreed 만 보내면 가입되지 않는다")
    void legacyFieldIsIgnored() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"pass1234!",
                                 "nickname":"김주장","phone":"010-1234-5678","termsAgreed":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents')]").exists());
    }

    @Test
    @DisplayName("동의를 안 했어도 다른 위반은 함께 보고된다 — 한 번에 다 고치게")
    void otherViolationsAreReportedTogether() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"pass1234!",
                                 "nickname":"김주장","phone":"010-1234-5678",
                                 "consents":{"age14":true,"terms":false,"privacy":true}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'consents.terms')]").exists());
    }

    /** 동의 기록이 없는 계정은 세 항목을 모두 받아야 한다 (소셜 최초 로그인의 모양). */
    @Test
    @DisplayName("기록이 없는 계정은 세 항목이 pendingConsents 에 온다")
    void accountWithoutRecordsNeedsAllThree() throws Exception {
        User social = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(social)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.pendingConsents",
                        org.hamcrest.Matchers.containsInAnyOrder("age14", "terms", "privacy")))
                .andExpect(jsonPath("$.termsAgreedAt").doesNotExist());
    }

    private ResultActions signup(String consentsJson) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"new@example.com","password":"pass1234!",
                         "nickname":"김주장","phone":"010-1234-5678","consents":%s}
                        """.formatted(consentsJson)));
    }

    private String consents(boolean age14, boolean terms, boolean privacy) {
        return "{\"age14\":%b,\"terms\":%b,\"privacy\":%b}".formatted(age14, terms, privacy);
    }
}
