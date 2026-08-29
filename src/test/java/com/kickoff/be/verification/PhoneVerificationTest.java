package com.kickoff.be.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.support.PhoneVerificationTestRepository;
import com.kickoff.be.verification.entity.PhoneVerification;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 전화번호 문자 인증 (계약서 §3-2, v1.15.0).
 *
 * 여기서 무겁게 보는 건 <b>실패가 실패로 남는가</b>다. 인증은 틀렸을 때 제대로 막히지
 * 않아도 화면상으로는 아무 문제가 없어 보인다 — 맞는 코드를 넣은 사람은 언제나 통과하고,
 * 틀린 사람만 통과하면 안 되는데 그건 아무도 눈으로 확인하지 않는다.
 *
 * 특히 <b>5회 실패 잠금</b>은 조용히 사라지기 쉽다. 실패 횟수를 올린 뒤 예외를 던지면
 * 트랜잭션이 되돌아 증가가 없던 일이 되는데, 응답은 400 으로 똑같아서 겉으로는 정상이다.
 * 그 상태에서는 6자리를 무제한으로 찍어볼 수 있다.
 */
class PhoneVerificationTest extends IntegrationTestSupport {

    private static final String PHONE = "010-1234-5678";

    @org.springframework.beans.factory.annotation.Autowired
    private PhoneVerificationTestRepository verificationClock;

    // ── 발송

    @Test
    @DisplayName("발송은 204 이고 응답에 코드가 없다 — 문자를 받은 사람만 알아야 한다")
    void sendReturnsNoContentWithoutCode() throws Exception {
        send(PHONE).andExpect(status().isNoContent())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .as("응답 본문이 비어 있어야 한다").isEmpty());

        assertThat(smsClient.sent()).hasSize(1);
        assertThat(smsClient.last().phone()).isEqualTo(PHONE);
        assertThat(smsClient.last().text())
                .isEqualTo("[킥오프] 인증번호 %s를 입력해 주세요.".formatted(smsClient.lastCode()));
        assertThat(smsClient.lastCode()).as("6자리 숫자").hasSize(6).containsOnlyDigits();
    }

    @Test
    @DisplayName("번호 형식이 틀리면 400 — signup 과 같은 규칙이다")
    void phoneFormatIsValidated() throws Exception {
        send("01012345678").andExpect(status().isBadRequest());
        send("").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("발송 실패는 502 이고 인증 행도 남기지 않는다")
    void sendFailureRollsBack() throws Exception {
        smsClient.willFail();

        mockMvc.perform(post("/api/auth/phone/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"" + PHONE + "\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SMS_SEND_FAILED"));

        // 못 보낸 발송이 한도에 세어지면, 문자도 못 받은 사람이 1분간 재시도도 못 한다
        assertThat(phoneVerificationRepository.count()).isZero();
    }

    // ── 확인

    @Test
    @DisplayName("맞는 코드면 verificationToken 을 준다")
    void confirmIssuesToken() throws Exception {
        send(PHONE).andExpect(status().isNoContent());

        confirm(PHONE, smsClient.lastCode())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationToken").isNotEmpty());
    }

    @Test
    @DisplayName("틀린 코드는 400 VERIFICATION_CODE_MISMATCH")
    void wrongCodeIsRejected() throws Exception {
        send(PHONE).andExpect(status().isNoContent());

        confirm(PHONE, wrongCode())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
    }

    @Test
    @DisplayName("5회 틀리면 코드가 죽는다 — 맞는 코드를 넣어도 통과하지 못한다")
    void codeDiesAfterFiveFailures() throws Exception {
        send(PHONE).andExpect(status().isNoContent());
        String correct = smsClient.lastCode();

        for (int i = 1; i <= PhoneVerification.MAX_ATTEMPTS; i++) {
            confirm(PHONE, wrongCode())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        }

        // 여기가 이 테스트의 전부다. 실패 횟수가 롤백으로 사라지면 이 줄이 200 이 되고,
        // 그 서버에서는 6자리를 무제한으로 대입할 수 있다
        confirm(PHONE, correct)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("발송한 적 없는 번호는 400 VERIFICATION_EXPIRED")
    void confirmWithoutSendIsExpired() throws Exception {
        confirm(PHONE, "123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("만료된 코드는 400 VERIFICATION_EXPIRED")
    void expiredCodeIsRejected() throws Exception {
        send(PHONE).andExpect(status().isNoContent());
        String code = smsClient.lastCode();
        expireCode();

        confirm(PHONE, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    @Test
    @DisplayName("재발송하면 이전 코드는 무효다")
    void resendInvalidatesPreviousCode() throws Exception {
        send(PHONE).andExpect(status().isNoContent());
        String first = smsClient.lastCode();

        allowImmediateResend();
        send(PHONE).andExpect(status().isNoContent());
        String second = smsClient.lastCode();

        // 두 코드가 우연히 같으면 이 테스트가 의미를 잃는다 — 100만 분의 1이라 그냥
        // 확인하고 넘어간다
        assertThat(first).isNotEqualTo(second);
        confirm(PHONE, first)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_CODE_MISMATCH"));
        confirm(PHONE, second).andExpect(status().isOk());
    }

    // ── 레이트리밋

    @Test
    @DisplayName("같은 번호로 1분에 두 번 보내면 429")
    void oneSendPerMinute() throws Exception {
        send(PHONE).andExpect(status().isNoContent());

        send(PHONE).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RATE_LIMITED"));

        // 다른 번호는 영향을 받지 않는다 — 한도는 번호 단위다
        send("010-9999-8888").andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("1시간에 5회를 넘으면 429")
    void fiveSendsPerHour() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send(PHONE).andExpect(status().isNoContent());
            allowImmediateResend();
        }

        send(PHONE).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_RATE_LIMITED"));
    }

    // ── 토큰

    @Test
    @DisplayName("토큰은 1회용이다 — 두 번째 가입에는 쓸 수 없다")
    void tokenIsSingleUse() throws Exception {
        String token = verify(PHONE);

        signup("one@example.com", PHONE, token).andExpect(status().isCreated());
        signup("two@example.com", PHONE, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("다른 번호로 받은 토큰은 통하지 않는다")
    void tokenIsBoundToItsPhone() throws Exception {
        String token = verify(PHONE);

        signup("other@example.com", "010-5555-6666", token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("만료된 토큰은 400 VERIFICATION_EXPIRED")
    void expiredTokenIsRejected() throws Exception {
        String token = verify(PHONE);
        expireToken();

        signup("late@example.com", PHONE, token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VERIFICATION_EXPIRED"));
    }

    // ── 스위치가 꺼져 있을 때 (기본값)

    @Test
    @DisplayName("스위치가 꺼져 있으면 토큰 없이도 가입된다 — 구버전 앱이 깨지지 않는다")
    void tokenIsOptionalWhileSwitchIsOff() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "old-client@example.com", "password": "pass1234",
                                 "nickname": "구버전", "phone": "010-7777-1234"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("꺼져 있어도 무효 토큰은 400 — 조용히 삼키지 않는다")
    void invalidTokenStillFailsWhileSwitchIsOff() throws Exception {
        // 삼켜 버리면 스위치를 켜는 날 갑자기 가입이 막히는 사용자가 생기고,
        // 그때는 언제부터 잘못됐는지 되짚을 방법이 없다
        signup("bogus@example.com", PHONE, "아무거나-만들어낸-토큰")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED"));
    }

    // ── 인증 경계

    @Test
    @DisplayName("두 API 는 인증 없이 열려 있다 — FE 의 구버전 감지가 여기 걸려 있다")
    void endpointsArePublic() throws Exception {
        // 401 이 나오면 FE 는 이 서버를 "§3-2 를 모르는 옛 서버"로 판정하고 인증 UI 를
        // 통째로 감춘다 (계약서 §3-2). 즉 permitAll 이 빠지면 기능이 조용히 사라진다
        send(PHONE).andExpect(status().isNoContent());
        confirm(PHONE, "000000").andExpect(status().isBadRequest());
    }

    // ── 헬퍼

    private ResultActions send(String phone) throws Exception {
        return mockMvc.perform(post("/api/auth/phone/verifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\"}"));
    }

    private ResultActions confirm(String phone, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/phone/verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\": \"" + phone + "\", \"code\": \"" + code + "\"}"));
    }

    private ResultActions signup(String email, String phone, String token) throws Exception {
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "pass1234", "nickname": "가입자",
                         "phone": "%s", "verificationToken": "%s"}
                        """.formatted(email, phone, token)));
    }

    /** 발송 → 확인까지 마치고 토큰을 돌려준다. */
    private String verify(String phone) throws Exception {
        send(phone).andExpect(status().isNoContent());
        String body = bodyOf(confirm(phone, smsClient.lastCode()).andExpect(status().isOk()));
        return JsonPath.parse(body).read("$.verificationToken");
    }

    /** 마지막 코드와 절대 같지 않은 6자리. */
    private String wrongCode() {
        String correct = smsClient.lastCode();
        return "000000".equals(correct) ? "111111" : "000000";
    }

    /** 3분·10분을 기다릴 수 없으니 저장된 만료 시각을 과거로 당긴다. */
    private void expireCode() {
        verificationClock.expireCodesAt(OffsetDateTime.now().minusMinutes(1));
    }

    private void expireToken() {
        verificationClock.expireTokensAt(OffsetDateTime.now().minusMinutes(1));
    }

    /** 1분 제한만 비켜 간다 — 1시간 창 안에는 그대로 남아야 5회 제한을 검증할 수 있다. */
    private void allowImmediateResend() {
        verificationClock.backdateSendsTo(OffsetDateTime.now().minusMinutes(2));
    }
}
