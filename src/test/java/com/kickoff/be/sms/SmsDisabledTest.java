package com.kickoff.be.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * SMS_ENABLED 가 꺼져 있으면 <b>어떤 어댑터가 붙어 있든</b> 문자가 나가지 않는다
 * (계약서 §3-2). PushDisabledTest 와 같은 취지다.
 *
 * 이 테스트가 생긴 이유는 실제로 그 상태를 만들었기 때문이다. 처음에는 SMS_ENABLED 검사를
 * LoggingSmsClient <b>안에</b> 뒀는데, 솔라피 어댑터를 @Primary 로 등록하니 그 클래스가
 * 통째로 대체되면서 검사도 같이 사라졌다. 키를 넣는 순간 dev 에서 실제 문자가 나가고
 * 잔액이 빠지는 상태였다 — 켜 보기 전에는 아무 증상이 없다.
 *
 * 그래서 스위치를 서비스로 올렸고, 여기서 그 자리를 못박는다. 검사가 다시 어댑터 쪽으로
 * 내려가면 이 테스트가 깨진다.
 */
@TestPropertySource(properties = "kickoff.sms.enabled=false")
class SmsDisabledTest extends IntegrationTestSupport {

    @Test
    @DisplayName("꺼져 있으면 어댑터를 부르지 않는다 — 204 는 그대로다")
    void sendsNothingWhenDisabled() throws Exception {
        smsClient.reset();

        mockMvc.perform(post("/api/auth/phone/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\": \"010-9000-0001\"}"))
                .andExpect(status().isNoContent());

        // 스텁이 비어 있어야 한다. 스텁 대신 실제 제공자가 붙어 있었다면 이 자리에서
        // 진짜 문자가 나갔을 것이다
        assertThat(smsClient.sent()).isEmpty();

        // 발송 자체는 성공으로 처리된다 — dev 는 코드를 로그로만 남기는 게 계약이다.
        // 인증 행도 남아야 재발송 제한과 확인이 그대로 돈다
        assertThat(phoneVerificationRepository.count()).isEqualTo(1);
    }
}
