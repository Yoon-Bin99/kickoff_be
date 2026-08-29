package com.kickoff.be.verification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 스위치를 켠 상태의 전화번호 인증 (계약서 §3-2, {@code PHONE_VERIFICATION_REQUIRED=true}).
 *
 * 켠 상태를 따로 두는 이유는, <b>이게 언젠가 운영의 기본값이 될 설정</b>이기 때문이다.
 * 기본값(false)만 검증하면 스위치를 켜는 날 처음으로 그 경로가 돌아가고, 그날 깨지면
 * 되돌리는 것 말고는 할 수 있는 게 없다.
 *
 * 롤아웃 순서상 BE 배포 → FE 배포 → preview 재빌드 → 전환이라, 전환은 한참 뒤에 일어난다.
 * 그 사이 코드가 바뀌어도 이 테스트가 전환 후의 동작을 계속 붙잡아 준다.
 */
@TestPropertySource(properties = "kickoff.verification.phone-required=true")
class PhoneVerificationRequiredTest extends IntegrationTestSupport {

    private static final String PHONE = "010-1234-5678";

    @Test
    @DisplayName("토큰 없는 가입은 400 PHONE_NOT_VERIFIED")
    void signupWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "no-token@example.com", "password": "pass1234!",
                                 "nickname": "무토큰", "phone": "%s"}
                                """.formatted(PHONE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("인증을 마친 토큰이면 가입된다")
    void signupWithTokenSucceeds() throws Exception {
        String token = verify(PHONE);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "verified@example.com", "password": "pass1234!",
                                 "nickname": "인증됨", "phone": "%s", "verificationToken": "%s"}
                                """.formatted(PHONE, token)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("phone 을 바꾸는 PATCH 는 토큰이 필요하다")
    void patchingPhoneNeedsToken() throws Exception {
        User user = createUser("patcher@example.com", "고치미", "010-0000-1111");
        String newPhone = "010-2222-3333";

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"" + newPhone + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));

        String token = verify(newPhone);
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"%s\", \"verificationToken\": \"%s\"}"
                                .formatted(newPhone, token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(newPhone));
    }

    @Test
    @DisplayName("phone 을 건드리지 않는 PATCH 는 토큰이 필요 없다")
    void patchingOtherFieldsNeedsNoToken() throws Exception {
        User user = createUser("nickonly@example.com", "닉만", "010-0000-2222");

        // 여기서 토큰을 요구하면 소셜 가입자가 닉네임 하나 고치려고 문자 인증을 해야 한다
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"바뀐닉\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("바뀐닉"));

        // 활동 지역만 바꾸는 요청도 같다 — 홈 화면의 지역 설정이 이 경로를 쓴다.
        // FE 는 phone 이 없는 요청에 토큰을 아예 싣지 않으므로, 서버가 여기서 토큰을
        // 요구하면 지역 설정이 그냥 깨진다 (FE 교차 검증에서 짚어 준 경로다).
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityRegion\": \"서울\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").value("서울"));

        // 지우는 것도 마찬가지다 (v1.6.0 의 명시적 null)
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityRegion\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").doesNotExist());
    }

    @Test
    @DisplayName("다른 번호로 받은 토큰으로는 번호를 바꿀 수 없다")
    void tokenMustMatchTheNewPhone() throws Exception {
        User user = createUser("mismatch@example.com", "불일치", "010-0000-3333");
        String token = verify(PHONE);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"010-4444-5555\", \"verificationToken\": \"%s\"}"
                                .formatted(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    /** 발송 → 확인까지 마치고 토큰을 돌려준다. */
    private String verify(String phone) throws Exception {
        mockMvc.perform(post("/api/auth/phone/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"" + phone + "\"}"))
                .andExpect(status().isNoContent());
        ResultActions confirmed = mockMvc.perform(
                        post("/api/auth/phone/verifications/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"phone\": \"%s\", \"code\": \"%s\"}"
                                        .formatted(phone, smsClient.lastCode())))
                .andExpect(status().isOk());
        return JsonPath.parse(bodyOf(confirmed)).read("$.verificationToken");
    }
}
