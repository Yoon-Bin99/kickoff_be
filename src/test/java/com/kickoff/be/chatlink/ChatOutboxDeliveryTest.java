package com.kickoff.be.chatlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import com.kickoff.be.chatlink.client.ChatServerClient;
import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import com.kickoff.be.chatlink.service.ChatOutboxDispatcher;
import com.kickoff.be.chatlink.service.ChatOutboxStore;
import com.kickoff.be.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * chat_outbox 에 쌓인 것을 실제로 내보내는 부분 (계약서 §10 "이벤트 전달").
 *
 * 채팅 서버를 띄우지 않고 클라이언트만 가짜로 바꾼다 — 보려는 것은 HTTP 가 아니라
 * <b>순서·재시도·정리</b>다. 스케줄러는 test 프로파일에서 꺼져 있으므로
 * ({@code SchedulingConfig @Profile("!test")}) 한 바퀴를 직접 돌린다.
 */
class ChatOutboxDeliveryTest extends IntegrationTestSupport {

    private static final ChatLinkProperties CONFIGURED =
            new ChatLinkProperties("test-secret", "http://localhost:18090");

    @Autowired
    private ChatOutboxStore store;

    @Test
    @DisplayName("보내고 나면 전달 완료로 표시되고 다시 집히지 않는다")
    void deliveredEventsAreMarkedAndNotRetried() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(true);

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(1);
        ChatOutbox event = only();
        assertThat(event.getDeliveredAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();

        // 두 번째 바퀴는 아무것도 집지 않는다
        dispatcher(client).dispatch();
        assertThat(client.sent).hasSize(1);
    }

    @Test
    @DisplayName("실패하면 행이 남고 다음 시도 시각이 뒤로 밀린다")
    void failureKeepsTheRowAndBacksOff() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(7L), queued));

        dispatcher(new RecordingClient(false)).dispatch();

        ChatOutbox event = only();
        assertThat(event.getDeliveredAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(queued);
    }

    /**
     * 백오프가 걸려 있는 동안은 집히지 않아야 한다. 안 그러면 "지수 백오프"가 이름뿐이고
     * 채팅 서버가 꺼져 있는 내내 5초마다 타임아웃을 쌓는다.
     */
    @Test
    @DisplayName("백오프 중인 행은 다음 바퀴에 집히지 않는다")
    void backedOffEventIsSkipped() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(false);

        dispatcher(client).dispatch();
        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(1);
    }

    /**
     * 한 건이 실패하면 보통 채팅 서버 자체가 안 떠 있다. 남은 49건까지 줄줄이 때리면 한
     * 바퀴가 타임아웃 × 50 이 되어 스케줄러가 몇 분씩 묶인다.
     */
    @Test
    @DisplayName("한 건이 실패하면 그 바퀴는 거기서 접는다")
    void oneFailureStopsTheRound() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(1L), queued));
        save(ChatOutbox.roomInvalidated(List.of(2L), queued));
        save(ChatOutbox.roomInvalidated(List.of(3L), queued));
        RecordingClient client = new RecordingClient(false);

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(1);
        assertThat(chatOutboxRepository.findAll())
                .allSatisfy(event -> assertThat(event.getDeliveredAt()).isNull());
    }

    @Test
    @DisplayName("오래된 순으로 보낸다")
    void sendsOldestFirst() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(1L), queued));
        save(ChatOutbox.roomInvalidated(List.of(2L), queued));
        save(ChatOutbox.roomInvalidated(List.of(3L), queued));
        RecordingClient client = new RecordingClient(true);

        dispatcher(client).dispatch();

        assertThat(client.sent).extracting(message -> message.requestIds().get(0))
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    @DisplayName("한 바퀴에 50건까지만 집는다")
    void batchIsCappedAtFifty() {
        OffsetDateTime queued = queued();
        for (long index = 1; index <= 55; index++) {
            save(ChatOutbox.roomInvalidated(List.of(index), queued));
        }
        RecordingClient client = new RecordingClient(true);

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(50);
    }

    /**
     * 계약서 §10 — 비밀값이 비면 전달도 멈춘다. <b>행은 쌓인다</b>: 버리면 비밀값을 넣은
     * 뒤에도 그동안의 취소·탈퇴를 채팅 서버가 영영 모른다.
     */
    @Test
    @DisplayName("비밀값이 없으면 한 건도 보내지 않고 행은 그대로 남는다")
    void nothingIsSentWithoutSecret() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(true);

        new ChatOutboxDispatcher(store, client,
                new ChatLinkProperties("", "http://localhost:18090")).dispatch();

        assertThat(client.sent).isEmpty();
        assertThat(only().getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("주소가 없어도 보내지 않는다")
    void nothingIsSentWithoutUrl() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(true);

        new ChatOutboxDispatcher(store, client, new ChatLinkProperties("secret", "")).dispatch();

        assertThat(client.sent).isEmpty();
    }

    @Test
    @DisplayName("전달 완료 후 7일이 지난 행만 지운다")
    void deletesOnlyOldDeliveredRows() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime queued = queued();
        Long oldId = save(delivered(now.minusDays(8)));
        Long freshId = save(delivered(now.minusDays(1)));
        Long pendingId = save(ChatOutbox.roomInvalidated(List.of(3L), queued));

        dispatcher(new RecordingClient(true)).deleteOldDelivered();

        assertThat(chatOutboxRepository.findAllById(List.of(oldId, freshId, pendingId)))
                .extracting(ChatOutbox::getId)
                .containsExactlyInAnyOrder(freshId, pendingId);
    }

    /**
     * 대기열에 들어간 지 조금 지난 행. <b>"지금"으로 만들지 않는다</b> — 저장되는 칸이
     * timestamp(6) 이라 마이크로초 아래가 반올림되는데, 윈도에서는 시계 해상도가 거칠어
     * 생성 시각과 스케줄러의 "지금"이 같은 값을 받는 일이 있다. 그러면 반올림 방향 하나로
     * 행이 "아직 보낼 때가 아닌" 쪽에 걸려 테스트가 가끔 깨진다. 실제로도 행은 생기자마자가
     * 아니라 다음 바퀴(최대 5초 뒤)에 집힌다.
     */
    private OffsetDateTime queued() {
        return OffsetDateTime.now().minusMinutes(1);
    }

    private ChatOutbox delivered(OffsetDateTime deliveredAt) {
        ChatOutbox event = ChatOutbox.roomInvalidated(List.of(1L), deliveredAt);
        event.markDelivered(deliveredAt);
        return event;
    }

    private Long save(ChatOutbox event) {
        return transactionTemplate.execute(status -> chatOutboxRepository.save(event).getId());
    }

    private ChatOutbox only() {
        List<ChatOutbox> events = chatOutboxRepository.findAll();
        assertThat(events).hasSize(1);
        return events.get(0);
    }

    private ChatOutboxDispatcher dispatcher(ChatServerClient client) {
        return new ChatOutboxDispatcher(store, client, CONFIGURED);
    }

    /** HTTP 대신 무엇이 넘어왔는지만 적어 둔다. */
    private static class RecordingClient extends ChatServerClient {

        private final List<ChatOutboxMessage> sent = new ArrayList<>();
        private final AtomicBoolean succeed = new AtomicBoolean();

        RecordingClient(boolean succeed) {
            super(CONFIGURED);
            this.succeed.set(succeed);
        }

        @Override
        public boolean send(ChatOutboxMessage message) {
            sent.add(message);
            return succeed.get();
        }
    }
}
