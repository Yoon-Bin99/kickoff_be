package com.kickoff.be.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * AI_SUPPORT_ENABLED 가 꺼져 있으면 <b>어댑터가 붙어 있어도</b> AI 를 부르지 않는다
 * (계약서 §7-1). SmsDisabledTest·PushDisabledTest 와 같은 취지다.
 *
 * 스위치를 어댑터 등록 조건에 넣지 않고 서비스가 보게 한 이유가 여기 있다. 등록 조건에
 * 넣으면 "키는 있는데 꺼 둔" 상태와 "키가 없는" 상태가 한 모양이 되어, 왜 AI 가 안 붙는지
 * 구별할 수 없다. 문자에서 정확히 그 실수를 했었다.
 *
 * <b>꺼져 있어도 문의는 정상 동작해야 한다.</b> AI 를 안 쓰는 배포에서 고객센터가 통째로
 * 막히면 안 된다 — FAQ 와 운영자 연결은 그대로다.
 */
@TestPropertySource(properties = "kickoff.ai-support.enabled=false")
class AiSupportDisabledTest extends IntegrationTestSupport {

    @Test
    @DisplayName("꺼져 있으면 AI 를 부르지 않고, 답변도 남기지 않는다")
    void aiStaysSilentWhenDisabled() throws Exception {
        User user = createUser("member@example.com", "김감독", "010-1111-1111");
        aiClient.reset();

        mockMvc.perform(post("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"질문 있습니다\"}"))
                .andExpect(status().isCreated());

        assertThat(aiClient.calls()).isEmpty();
        // 강등 안내조차 남기지 않는다 — 남기면 AI 를 안 쓰는 배포의 모든 방에 안내가 쌓인다
        assertThat(supportMessageRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("꺼져 있어도 FAQ 와 문의 자체는 그대로 동작한다")
    void faqAndChatStillWork() throws Exception {
        User user = createUser("member2@example.com", "이감독", "010-2222-2222");

        mockMvc.perform(get("/api/support/faq")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].question").isNotEmpty());

        mockMvc.perform(post("/api/support/chat/escalate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent());
    }
}
