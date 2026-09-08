package com.kickoff.be.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 약관·개인정보처리방침 동의 (계약서 §3, v1.21.0).
 *
 * 동의는 <b>받았다는 사실보다 언제 받았는지가 기록</b>이다. 나중에 분쟁이 나면 그 시각이
 * 근거가 되므로, 지어내지도 빠뜨리지도 않는 것이 이 기능의 전부다.
 */
class TermsAgreementTest extends IntegrationTestSupport {

    @Test
    @DisplayName("동의하면 가입되고 동의 시각이 남는다")
    void agreeingRecordsTheMoment() throws Exception {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);

        signup("\"termsAgreed\": true").andExpect(status().isCreated());

        User saved = userRepository.findByEmail("terms@example.com").orElseThrow();
        assertThat(saved.getTermsAgreedAt())
                .as("동의 시각")
                .isNotNull()
                .isAfterOrEqualTo(before);
    }

    /**
     * <b>v1.25.0 에서 뒤집힌 규칙이다.</b> v1.21.0 때는 "화면이 쓸 값이 아니라 우리가
     * 보관하는 기록"이라고 보고 응답에서 뺐다. 그때는 이메일 가입만 동의 절차를 지났으니
     * 맞는 판단이었다 — 서버가 이미 받아 둔 값을 FE 가 다시 볼 이유가 없었다.
     *
     * 그런데 소셜 가입에는 동의 절차가 아예 없었다는 게 드러났다. 그래서 FE 가 "이 계정이
     * 동의했는가"를 물어야 하는 상황이 생겼고, 그 답이 이 필드다. 값의 성격이 바뀐 게 아니라
     * <b>쓰는 쪽이 늘었다.</b> 재동의 판정(현행 시행일과 비교)도 같은 값으로 한다.
     */
    @Test
    @DisplayName("동의 시각이 응답에 실린다 (v1.25.0) — FE 가 동의 게이트를 판정하는 값이다")
    void agreedAtIsExposed() throws Exception {
        signup("\"termsAgreed\": true")
                .andExpect(jsonPath("$.user.termsAgreedAt").isNotEmpty());
    }

    @Test
    @DisplayName("동의하지 않으면 400 — 안내 문구가 fieldErrors 에 담긴다")
    void refusingIsRejected() throws Exception {
        signup("\"termsAgreed\": false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'termsAgreed')]").exists());

        assertThat(userRepository.findByEmail("terms@example.com")).isEmpty();
    }

    @Test
    @DisplayName("필드를 아예 안 보내도 400 — 다만 안내가 다르다")
    void omittingIsRejectedWithDifferentGuidance() throws Exception {
        // 누락은 구버전 앱이 보내는 모양이라 "동의해 주세요"가 아니라 "업데이트해 주세요"가
        // 맞는 안내다. primitive boolean 으로 받았으면 누락이 false 로 바인딩돼 둘이
        // 구별되지 않는다 — 사용자는 동의 체크박스를 찾다가 없어서 막힌다.
        String body = signup(null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("업데이트");
        assertThat(body).doesNotContain("동의해야 가입할 수 있습니다");
    }

    @Test
    @DisplayName("거부는 '동의해 주세요'로 안내한다")
    void refusingSaysAgree() throws Exception {
        String body = signup("\"termsAgreed\": false").andReturn()
                .getResponse().getContentAsString();

        assertThat(body).contains("동의해야 가입할 수 있습니다");
        assertThat(body).doesNotContain("업데이트");
    }

    @Test
    @DisplayName("동의를 안 했어도 다른 위반은 함께 보고된다 — 한 번에 다 고치게")
    void otherViolationsStillReported() throws Exception {
        // 동의만 먼저 걸러 내면 사용자가 고치고 다시 눌렀을 때 또 다른 오류를 만난다
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short",
                                 "nickname": "김", "phone": "01012345678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'termsAgreed')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    private ResultActions signup(String termsField) throws Exception {
        String body = """
                {"email": "terms@example.com", "password": "pass1234!",
                 "nickname": "동의자", "phone": "010-8888-1234"%s}
                """.formatted(termsField == null ? "" : ", " + termsField);
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
