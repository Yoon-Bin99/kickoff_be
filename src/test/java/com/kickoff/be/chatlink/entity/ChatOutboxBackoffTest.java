package com.kickoff.be.chatlink.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 재시도 간격과 id 목록 담기 (계약서 §10). 스프링 없이 도는 순수 단위 테스트다.
 */
class ChatOutboxBackoffTest {

    @Test
    @DisplayName("재시도 간격은 5초에서 두 배씩 늘어난다")
    void backoffDoubles() {
        assertThat(ChatOutbox.backoff(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(ChatOutbox.backoff(2)).isEqualTo(Duration.ofSeconds(10));
        assertThat(ChatOutbox.backoff(3)).isEqualTo(Duration.ofSeconds(20));
        assertThat(ChatOutbox.backoff(4)).isEqualTo(Duration.ofSeconds(40));
    }

    /**
     * 계약서가 정한 상한이다. 없으면 며칠 꺼져 있던 채팅 서버가 살아나도 몇 시간 뒤에야
     * 다음 시도가 온다 — 그동안 탈퇴한 사람의 대화가 그대로 남는다.
     */
    @Test
    @DisplayName("재시도 간격은 5분을 넘지 않는다")
    void backoffIsCappedAtFiveMinutes() {
        assertThat(ChatOutbox.backoff(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(ChatOutbox.backoff(1000)).isEqualTo(Duration.ofMinutes(5));
        // 지수가 long 을 넘겨 음수가 되면 "즉시 재시도"가 되어 상한이 사라진다
        assertThat(ChatOutbox.backoff(Integer.MAX_VALUE)).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("실패할 때마다 간격이 늘어난다")
    void repeatedFailuresGrowTheGap() {
        OffsetDateTime now = OffsetDateTime.now();
        ChatOutbox event = ChatOutbox.roomInvalidated(List.of(7L), now);

        event.markFailed(now);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));

        event.markFailed(now);
        assertThat(event.getNextAttemptAt()).isEqualTo(now.plusSeconds(10));
    }

    @Test
    @DisplayName("id 목록은 넣은 순서 그대로 돌아온다")
    void idsRoundTrip() {
        ChatOutbox event = ChatOutbox.purge(List.of(7L, 9L, 15L), List.of(3L),
                OffsetDateTime.now());

        assertThat(event.requestIdList()).containsExactly(7L, 9L, 15L);
        assertThat(event.teamIdList()).containsExactly(3L);
    }

    @Test
    @DisplayName("빈 목록은 빈 목록으로 돌아온다")
    void emptyListsRoundTrip() {
        ChatOutbox event = ChatOutbox.roomInvalidated(List.of(7L), OffsetDateTime.now());

        assertThat(event.teamIdList()).isEmpty();
    }
}
