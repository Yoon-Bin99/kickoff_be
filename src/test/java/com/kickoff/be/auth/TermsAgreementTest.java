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

    @Test
    @DisplayName("동의 시각은 응답에 나가지 않는다 — 화면이 쓸 값이 아니라 우리가 보관하는 기록이다")
    void agreedAtIsNotExposed() throws Exception {
        String body = signup("\"termsAgreed\": true").andReturn()
                .getResponse().getContentAsString();

        assertThat(body).doesNotContain("termsAgreedAt", "termsAgreed");
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
