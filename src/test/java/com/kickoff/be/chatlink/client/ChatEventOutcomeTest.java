package com.kickoff.be.chatlink.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.chatlink.dto.ChatEventOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 응답 상태를 "다음에 무엇을 할지"로 옮기는 규칙 (계약서 §10).
 *
 * 이 한 줄에 전달의 성패가 달려 있다. 재시도해야 할 것을 포기하면 <b>취소·탈퇴 알림이
 * 조용히 버려지고</b>, 포기해야 할 것을 재시도하면 그 행 하나가 5초마다 같은 400 을 받아
 * 오면서 영원히 남는다.
 */
class ChatEventOutcomeTest {

    @Test
    @DisplayName("2xx 아닌 5xx 는 재시도다")
    void serverErrorsRetry() {
        assertThat(ChatServerClient.outcomeOf(500)).isEqualTo(ChatEventOutcome.RETRY);
        assertThat(ChatServerClient.outcomeOf(502)).isEqualTo(ChatEventOutcome.RETRY);
        assertThat(ChatServerClient.outcomeOf(503)).isEqualTo(ChatEventOutcome.RETRY);
    }

    @Test
    @DisplayName("401 은 따로 본다 — 비밀값이 어긋난 것이라 경고가 필요하다")
    void unauthorizedIsItsOwnCase() {
        assertThat(ChatServerClient.outcomeOf(401)).isEqualTo(ChatEventOutcome.UNAUTHORIZED);
    }

    @Test
    @DisplayName("그 밖의 4xx 는 포기 대상이다")
    void clientErrorsAreRejected() {
        assertThat(ChatServerClient.outcomeOf(400)).isEqualTo(ChatEventOutcome.REJECTED);
        assertThat(ChatServerClient.outcomeOf(403)).isEqualTo(ChatEventOutcome.REJECTED);
        assertThat(ChatServerClient.outcomeOf(404)).isEqualTo(ChatEventOutcome.REJECTED);
        assertThat(ChatServerClient.outcomeOf(415)).isEqualTo(ChatEventOutcome.REJECTED);
    }

    /**
     * 둘 다 "지금은 안 되니 나중에"라는 뜻이다. 400 과 같이 취급하면 멀쩡한 이벤트를 다섯
     * 번 만에 버리는데, 429 는 특히 <b>우리가 50건을 몰아 보낸 직후</b>에 나올 수 있는 답이다.
     */
    @Test
    @DisplayName("408·429 는 4xx 지만 재시도다")
    void timeoutAndThrottleRetry() {
        assertThat(ChatServerClient.outcomeOf(408)).isEqualTo(ChatEventOutcome.RETRY);
        assertThat(ChatServerClient.outcomeOf(429)).isEqualTo(ChatEventOutcome.RETRY);
    }

    /**
     * 3xx 는 계약에 없다. 포기하는 쪽으로 기울면 리다이렉트를 끼운 프록시 하나에 이벤트가
     * 버려지므로, 모르는 것은 재시도로 둔다 — 쌓이는 쪽은 사람이 알아챌 수 있다.
     */
    @Test
    @DisplayName("계약에 없는 상태는 재시도 쪽으로 둔다")
    void unknownStatusesRetry() {
        assertThat(ChatServerClient.outcomeOf(302)).isEqualTo(ChatEventOutcome.RETRY);
    }
}
