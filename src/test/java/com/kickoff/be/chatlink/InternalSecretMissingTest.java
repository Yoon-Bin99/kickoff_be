package com.kickoff.be.chatlink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.chatlink.security.InternalSecretFilter;
import com.kickoff.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 공유 비밀이 비어 있으면 {@code /internal/**} 이 <b>전부</b> 401 이다 (계약서 §10).
 *
 * 계약서가 "닫힌 쪽으로 실패"라고 못박은 지점이다. 반대로 만들면 — 비밀값이 없을 때
 * 검사를 건너뛰게 두면 — <b>설정을 빠뜨린 배포가 내부 API 를 누구에게나 열어 준다.</b>
 * 그리고 그 상태는 증상이 없다: 채팅은 잘 돌고, 밖에서 요청이 들어오기 전까지 아무도
 * 모른다. 운영에 기본값이 없는 것도 같은 이유다(dev 프로파일에만 있다).
 *
 * {@code SmsDisabledTest} 와 같은 방식으로 프로퍼티만 덮어 컨텍스트를 따로 띄운다.
 */
@TestPropertySource(properties = "chat.internal.secret=")
class InternalSecretMissingTest extends IntegrationTestSupport {

    /**
     * 빈 문자열을 보내면 통과하는 구멍이 가장 위험하다 — 비밀값이 없는 서버에
     * {@code X-Internal-Secret: } 한 줄이면 들어가진다는 뜻이기 때문이다.
     */
    @Test
    @DisplayName("비밀값이 없으면 빈 헤더로도 들어올 수 없다")
    void emptyHeaderDoesNotMatchEmptySecret() throws Exception {
        mockMvc.perform(get("/internal/chat/rooms/1")
                        .header(InternalSecretFilter.HEADER, ""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비밀값이 없으면 헤더 없는 요청도 401 이다")
    void withoutHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/chat/users/1/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("비밀값이 없으면 푸시 요청도 401 이다")
    void pushIsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/chat/push")
                        .header(InternalSecretFilter.HEADER, "아무거나")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientUserId\":1,\"requestId\":1,\"postId\":1,"
                                + "\"senderTeamName\":\"FC 새벽\",\"content\":\"안녕하세요\"}"))
                .andExpect(status().isUnauthorized());
    }
}
