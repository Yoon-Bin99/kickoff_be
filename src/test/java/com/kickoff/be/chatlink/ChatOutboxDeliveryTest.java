package com.kickoff.be.chatlink;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import com.kickoff.be.chatlink.client.ChatOutboxProperties;
import com.kickoff.be.chatlink.client.ChatServerClient;
import com.kickoff.be.chatlink.dto.ChatEventOutcome;
import com.kickoff.be.chatlink.dto.ChatEventResult;
import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import com.kickoff.be.chatlink.service.ChatOutboxDispatcher;
import com.kickoff.be.chatlink.service.ChatOutboxStore;
import com.kickoff.be.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * chat_outbox 에 쌓인 것을 실제로 내보내는 부분 (계약서 §10 "이벤트 전달").
 *
 * 채팅 서버를 띄우지 않고 클라이언트만 가짜로 바꾼다 — 보려는 것은 HTTP 가 아니라
 * <b>응답 종류별 상태 전이·순서·재시도·정리</b>다. 스케줄러는 test 프로파일에서 꺼져 있으므로
 * ({@code SchedulingConfig @Profile("!test")}) 한 바퀴를 직접 돌린다.
 */
class ChatOutboxDeliveryTest extends IntegrationTestSupport {

    private static final ChatLinkProperties CONFIGURED =
            new ChatLinkProperties("test-secret", "http://localhost:18090");
    private static final ChatOutboxProperties OUTBOX =
            new ChatOutboxProperties(Duration.ofHours(1));

    @Autowired
    private ChatOutboxStore store;

    @Test
    @DisplayName("보내고 나면 전달 완료로 표시되고 다시 집히지 않는다")
    void deliveredEventsAreMarkedAndNotRetried() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(1);
        ChatOutbox event = only();
        assertThat(event.getDeliveredAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();

        // 두 번째 바퀴는 아무것도 집지 않는다
        dispatcher(client).dispatch();
        assertThat(client.sent).hasSize(1);
    }

    // ── 응답 종류별 상태 전이 (계약서 §10)

    @Test
    @DisplayName("5xx·타임아웃은 횟수 제한 없이 재시도한다")
    void serverErrorsRetryForever() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));

        // 백오프를 넘기려고 매 바퀴 행의 다음 시도 시각을 되돌린다
        for (int round = 1; round <= ChatOutbox.DEAD_AFTER_ATTEMPTS + 3; round++) {
            dispatcher(new RecordingClient(ChatEventResult.noResponse())).dispatch();
            rewindNextAttempt();
        }

        ChatOutbox event = only();
        assertThat(event.getAttempts()).isGreaterThan(ChatOutbox.DEAD_AFTER_ATTEMPTS);
        assertThat(event.isDead()).as("5xx 는 몇 번을 실패해도 포기하지 않는다").isFalse();
        assertThat(event.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("401 은 설정 사고라 재시도를 유지한다 — 포기하지 않는다")
    void unauthorizedKeepsRetrying() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));

        for (int round = 1; round <= ChatOutbox.DEAD_AFTER_ATTEMPTS + 1; round++) {
            dispatcher(new RecordingClient(
                    ChatEventResult.of(ChatEventOutcome.UNAUTHORIZED, 401))).dispatch();
            rewindNextAttempt();
        }

        ChatOutbox event = only();
        assertThat(event.isDead()).isFalse();
        assertThat(event.getAttempts()).isGreaterThan(ChatOutbox.DEAD_AFTER_ATTEMPTS);
    }

    /**
     * 모르는 type·깨진 본문에 돌아오는 답이다. 기다린다고 달라지지 않으므로 몇 번 가 보고
     * 그만둔다 — 안 그러면 그 행 하나가 5초마다 400 을 받아 오면서 영원히 남는다.
     */
    @Test
    @DisplayName("400 은 다섯 번째 시도에서 포기(dead)한다")
    void rejectedGoesDeadAfterFiveAttempts() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(
                ChatEventResult.of(ChatEventOutcome.REJECTED, 400));

        for (int round = 1; round < ChatOutbox.DEAD_AFTER_ATTEMPTS; round++) {
            dispatcher(client).dispatch();
            assertThat(only().isDead()).as("%d번째 시도에서는 아직 살아 있어야 한다", round).isFalse();
            rewindNextAttempt();
        }
        dispatcher(client).dispatch();

        ChatOutbox event = only();
        assertThat(event.isDead()).isTrue();
        assertThat(event.getAttempts()).isEqualTo(ChatOutbox.DEAD_AFTER_ATTEMPTS);
        assertThat(event.getDeliveredAt()).as("포기는 전달이 아니다").isNull();
        assertThat(client.sent).hasSize(ChatOutbox.DEAD_AFTER_ATTEMPTS);
    }

    @Test
    @DisplayName("포기한 행은 다시 보내지 않는다")
    void deadEventIsNeverSentAgain() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(
                ChatEventResult.of(ChatEventOutcome.REJECTED, 400));
        for (int round = 0; round < ChatOutbox.DEAD_AFTER_ATTEMPTS; round++) {
            dispatcher(client).dispatch();
            rewindNextAttempt();
        }
        assertThat(only().isDead()).isTrue();

        RecordingClient after = new RecordingClient(ChatEventResult.delivered(204));
        dispatcher(after).dispatch();

        assertThat(after.sent).isEmpty();
    }

    /**
     * 막힌 행 하나가 뒤의 모든 이벤트를 붙잡으면, 취소·탈퇴 알림이 그 행이 dead 가 될
     * 때까지 전부 멈춘다.
     */
    @Test
    @DisplayName("한 건이 거절당해도 뒤 행은 계속 나간다")
    void oneRejectionDoesNotBlockTheRest() {
        OffsetDateTime queued = queued();
        Long blocked = save(ChatOutbox.roomInvalidated(List.of(1L), queued));
        Long second = save(ChatOutbox.roomInvalidated(List.of(2L), queued));
        Long third = save(ChatOutbox.roomInvalidated(List.of(3L), queued));
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));
        client.reject(blocked);

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(3);
        assertThat(find(blocked).getDeliveredAt()).isNull();
        assertThat(find(second).getDeliveredAt()).isNotNull();
        assertThat(find(third).getDeliveredAt()).isNotNull();
    }

    @Test
    @DisplayName("채팅 서버가 죽어 전부 실패해도 배치 끝까지 가 본다")
    void transportFailureStillWalksTheBatch() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(1L), queued));
        save(ChatOutbox.roomInvalidated(List.of(2L), queued));
        save(ChatOutbox.roomInvalidated(List.of(3L), queued));
        RecordingClient client = new RecordingClient(ChatEventResult.noResponse());

        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(3);
        assertThat(chatOutboxRepository.findAll())
                .allSatisfy(event -> assertThat(event.getAttempts()).isEqualTo(1));
    }

    @Test
    @DisplayName("실패하면 행이 남고 다음 시도 시각이 뒤로 밀린다")
    void failureKeepsTheRowAndBacksOff() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(7L), queued));

        dispatcher(new RecordingClient(ChatEventResult.noResponse())).dispatch();

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
        RecordingClient client = new RecordingClient(ChatEventResult.noResponse());

        dispatcher(client).dispatch();
        dispatcher(client).dispatch();

        assertThat(client.sent).hasSize(1);
    }

    @Test
    @DisplayName("오래된 순으로 보낸다")
    void sendsOldestFirst() {
        OffsetDateTime queued = queued();
        save(ChatOutbox.roomInvalidated(List.of(1L), queued));
        save(ChatOutbox.roomInvalidated(List.of(2L), queued));
        save(ChatOutbox.roomInvalidated(List.of(3L), queued));
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));

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
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));

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
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));

        new ChatOutboxDispatcher(store, client,
                new ChatLinkProperties("", "http://localhost:18090"), OUTBOX).dispatch();

        assertThat(client.sent).isEmpty();
        assertThat(only().getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("주소가 없어도 보내지 않는다")
    void nothingIsSentWithoutUrl() {
        save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        RecordingClient client = new RecordingClient(ChatEventResult.delivered(204));

        new ChatOutboxDispatcher(store, client, new ChatLinkProperties("secret", ""), OUTBOX)
                .dispatch();

        assertThat(client.sent).isEmpty();
    }

    // ── 정리 (계약서 §10 "완료 후 7일")

    @Test
    @DisplayName("전달 완료 후 7일이 지난 행만 지운다")
    void deletesOnlyOldDeliveredRows() {
        OffsetDateTime now = OffsetDateTime.now();
        Long oldId = save(delivered(now.minusDays(8)));
        Long freshId = save(delivered(now.minusDays(1)));
        Long pendingId = save(ChatOutbox.roomInvalidated(List.of(3L), queued()));

        dispatcher(new RecordingClient(ChatEventResult.delivered(204))).deleteOldDelivered();

        assertThat(chatOutboxRepository.findAllById(List.of(oldId, freshId, pendingId)))
                .extracting(ChatOutbox::getId)
                .containsExactlyInAnyOrder(freshId, pendingId);
    }

    /**
     * 포기한 행이야말로 남아 있어야 한다. 특히 PURGE 가 dead 면 <b>탈퇴한 사람의 대화가
     * 채팅 서버에 남아 있다</b>는 뜻이라, 행을 지우면 무엇을 지워야 하는지조차 알 수 없다.
     */
    @Test
    @DisplayName("포기한 행은 오래돼도 지우지 않는다")
    void deadRowsSurviveCleanup() {
        Long deadId = save(dead());

        dispatcher(new RecordingClient(ChatEventResult.delivered(204))).deleteOldDelivered();

        assertThat(chatOutboxRepository.findById(deadId)).isPresent();
    }

    // ── 탈퇴 대화 삭제 지연 경보 (법률 점검 11절)

    @Test
    @DisplayName("1시간이 안 됐으면 알리지 않는다")
    void freshPurgeIsNotAlerted() {
        Long id = save(ChatOutbox.purge(List.of(7L), List.of(3L), queued()));
        ageTo(id, OffsetDateTime.now().minusMinutes(59));

        assertThat(alerts()).isZero();
    }

    @Test
    @DisplayName("1시간이 넘으면 알린다")
    void overduePurgeIsAlerted() {
        Long id = save(ChatOutbox.purge(List.of(7L), List.of(3L), queued()));
        ageTo(id, OffsetDateTime.now().minusMinutes(61));

        assertThat(alerts()).isEqualTo(1);
    }

    /**
     * 1분마다 도는 점검이라, 억제가 없으면 같은 행 하나로 하루 1440줄이 쌓인다 — 그러면
     * 경보가 아니라 소음이고, 새로 생긴 건이 그 안에 묻힌다.
     */
    @Test
    @DisplayName("같은 행은 간격 안에 다시 알리지 않는다")
    void repeatedAlertsAreSuppressed() {
        Long id = save(ChatOutbox.purge(List.of(7L), List.of(3L), queued()));
        ageTo(id, OffsetDateTime.now().minusMinutes(61));
        ChatOutboxDispatcher dispatcher = dispatcher(
                new RecordingClient(ChatEventResult.noResponse()));

        assertThat(dispatcher.alertOverduePurges()).isEqualTo(1);
        assertThat(dispatcher.alertOverduePurges()).isZero();
        assertThat(dispatcher.alertOverduePurges()).isZero();
    }

    @Test
    @DisplayName("억제는 행 단위다 — 다른 행은 같은 바퀴에 함께 알린다")
    void eachOverdueRowGetsItsOwnAlert() {
        Long first = save(ChatOutbox.purge(List.of(7L), List.of(3L), queued()));
        Long second = save(ChatOutbox.purge(List.of(9L), List.of(4L), queued()));
        ageTo(first, OffsetDateTime.now().minusMinutes(61));
        ageTo(second, OffsetDateTime.now().minusMinutes(90));

        assertThat(alerts()).isEqualTo(2);
    }

    /**
     * ROOM_INVALIDATED 가 늦는 것은 채팅 서버의 15초 캐시가 메워 준다. 여기까지 경보를
     * 올리면 정작 봐야 할 PURGE 가 그 안에 묻힌다.
     */
    @Test
    @DisplayName("ROOM_INVALIDATED 는 아무리 묵어도 알리지 않는다")
    void roomInvalidatedIsNeverAlerted() {
        Long id = save(ChatOutbox.roomInvalidated(List.of(7L), queued()));
        ageTo(id, OffsetDateTime.now().minusDays(3));

        assertThat(alerts()).isZero();
    }

    @Test
    @DisplayName("전달이 끝난 PURGE 는 알리지 않는다")
    void deliveredPurgeIsNotAlerted() {
        ChatOutbox event = ChatOutbox.purge(List.of(7L), List.of(3L), queued());
        event.markDelivered(OffsetDateTime.now());
        Long id = save(event);
        ageTo(id, OffsetDateTime.now().minusDays(3));

        assertThat(alerts()).isZero();
    }

    /** 포기한 PURGE 야말로 사람이 손대야 한다 — 조용해지면 아무도 모른다. */
    @Test
    @DisplayName("포기한 PURGE 도 계속 알린다")
    void deadPurgeIsStillAlerted() {
        ChatOutbox event = ChatOutbox.purge(List.of(7L), List.of(3L), queued());
        for (int attempt = 0; attempt < ChatOutbox.DEAD_AFTER_ATTEMPTS; attempt++) {
            event.markRejected(OffsetDateTime.now());
        }
        Long id = save(event);
        ageTo(id, OffsetDateTime.now().minusMinutes(61));

        assertThat(alerts()).isEqualTo(1);
    }

    // ── 거들기

    private int alerts() {
        return dispatcher(new RecordingClient(ChatEventResult.noResponse())).alertOverduePurges();
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

    /** 백오프를 건너뛰고 다음 바퀴에 다시 집히게 한다 — 시계를 밀 수 없으니 행을 당긴다. */
    private void rewindNextAttempt() {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update ChatOutbox o set o.nextAttemptAt = :when")
                .setParameter("when", OffsetDateTime.now().minusHours(1))
                .executeUpdate());
    }

    /** 생성 시각을 과거로 민다. {@code @CreatedDate} 는 벌크 update 를 타지 않는다. */
    private void ageTo(Long id, OffsetDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update ChatOutbox o set o.createdAt = :when where o.id = :id")
                .setParameter("when", createdAt)
                .setParameter("id", id)
                .executeUpdate());
    }

    private ChatOutbox delivered(OffsetDateTime deliveredAt) {
        ChatOutbox event = ChatOutbox.roomInvalidated(List.of(1L), deliveredAt);
        event.markDelivered(deliveredAt);
        return event;
    }

    private ChatOutbox dead() {
        OffsetDateTime longAgo = OffsetDateTime.now().minusDays(30);
        ChatOutbox event = ChatOutbox.roomInvalidated(List.of(1L), longAgo);
        for (int attempt = 0; attempt < ChatOutbox.DEAD_AFTER_ATTEMPTS; attempt++) {
            event.markRejected(longAgo);
        }
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

    private ChatOutbox find(Long id) {
        return chatOutboxRepository.findById(id).orElseThrow();
    }

    private ChatOutboxDispatcher dispatcher(ChatServerClient client) {
        return new ChatOutboxDispatcher(store, client, CONFIGURED, OUTBOX);
    }

    /** HTTP 대신 무엇이 넘어왔는지만 적어 두고, 미리 정한 답을 돌려준다. */
    private static class RecordingClient extends ChatServerClient {

        private final List<ChatOutboxMessage> sent = new ArrayList<>();
        private final AtomicReference<ChatEventResult> answer = new AtomicReference<>();
        private final List<Long> rejected = new ArrayList<>();

        RecordingClient(ChatEventResult answer) {
            super(CONFIGURED);
            this.answer.set(answer);
        }

        /** 이 이벤트에만 400 을 준다 — 막힌 행이 뒤를 붙잡는지 보려는 것이다. */
        void reject(Long eventId) {
            rejected.add(eventId);
        }

        @Override
        public ChatEventResult send(ChatOutboxMessage message) {
            sent.add(message);
            return rejected.contains(message.eventId())
                    ? ChatEventResult.of(ChatEventOutcome.REJECTED, 400)
                    : answer.get();
        }
    }
}
