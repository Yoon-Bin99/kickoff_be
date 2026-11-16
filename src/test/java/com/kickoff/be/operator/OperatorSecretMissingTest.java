package com.kickoff.be.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 비밀키가 없으면 운영자 API 가 전부 막힌다 (계약서 §7-2, v1.35.1).
 *
 * <b>닫힌 쪽으로 실패한다.</b> 설정을 빠뜨린 배포가 남의 문의와 신고된 대화를 열어 주지
 * 않게 하려는 것이다 — 채팅 내부 비밀값(§10)과 같은 태도다.
 *
 * {@code SmsDisabledTest} 와 같은 방식으로 이 테스트만 설정을 덮는다.
 */
@TestPropertySource(properties = "kickoff.operator.secret=")
class OperatorSecretMissingTest extends IntegrationTestSupport {

    /**
     * 401 이 아니라 403 이다. 401 이면 FE 가 OTP 화면을 띄우는데, 비밀키가 없으면 어떤
     * 코드도 통과하지 못해 빠져나올 수 없는 고리가 된다 — 설정 사고는 사용자가 풀 수
     * 있는 일이 아니다.
     */
    @Test
    @DisplayName("비밀키가 없으면 문의함이 403 이다 — 401 이 아니다")
    void supportInboxIsForbidden() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");

        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("세션도 열 수 없다")
    void sessionCannotBeOpened() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");

        mockMvc.perform(post("/api/admin/session")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otp\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }
}
