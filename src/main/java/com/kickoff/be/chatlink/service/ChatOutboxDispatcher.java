package com.kickoff.be.chatlink.service;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import com.kickoff.be.chatlink.client.ChatOutboxProperties;
import com.kickoff.be.chatlink.client.ChatServerClient;
import com.kickoff.be.chatlink.dto.ChatEventResult;
import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.dto.OverduePurge;
import com.kickoff.be.chatlink.entity.ChatEventType;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * chat_outbox 에 쌓인 이벤트를 채팅 서버로 보낸다 (계약서 §10).
 *
 * 한 바퀴는 "읽기(트랜잭션) → 보내기(트랜잭션 없음) → 표시(건별 트랜잭션)"다. HTTP 왕복
 * 동안 DB 커넥션을 쥐지 않으려는 구조이고, 경계를 실제로 가르려고 읽기·표시는
 * {@link ChatOutboxStore} 에 있다.
 *
 * <b>한 건이 실패해도 배치를 멈추지 않는다.</b> 멈추면 맨 앞의 막힌 행 하나가 뒤의 모든
 * 이벤트를 붙잡는다 — 채팅 서버가 영영 못 받는 행(dead 가 되기 전 400)이 정확히 그 모양이다.
 * 대신 한 바퀴에 {@link #ROUND_BUDGET} 이라는 전체 시간 예산을 둔다. 채팅 서버가 안 뜬
 * 날에는 모든 호출이 타임아웃이라, 예산이 없으면 한 바퀴가 50 × 타임아웃이 되어 5초
 * 스케줄러가 몇 분씩 묶인다.
 *
 * <b>인스턴스가 하나라는 전제다.</b> 여럿이면 같은 행을 동시에 집어 같은 이벤트가 두 번
 * 나갈 수 있다 — 처리가 멱등이라 결과는 같지만 쓸모없는 일이다. 늘릴 때는 행 잠금
 * ({@code select ... for update skip locked})이 필요하다. README 에 전제로 적어 뒀다.
 *
 * <b>로그를 도배하지 않는다.</b> 채팅 서버가 꺼져 있으면 5초마다 실패하는데, 그걸 그대로
 * warn 으로 찍으면 하루에 만 줄이 넘고 <b>다른 경고가 그 안에 묻힌다.</b> 그래서 끊긴
 * 순간과 복구된 순간만 알리고, 그사이에는 {@link #WARN_INTERVAL} 마다 한 줄만 남긴다.
 * 비밀값 불일치(401)와 PURGE 지연 경보도 같은 방식으로 억제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOutboxDispatcher {

    /** 계약서 §10 — 5초마다 오래된 순으로 최대 50개. */
    static final int BATCH_SIZE = 50;

    /** 계약서 §10 — 전송 완료 후 7일 지난 행은 지운다. */
    private static final Duration RETENTION = Duration.ofDays(7);

    /** 끊긴 동안 다시 알리는 간격. 이 값만큼은 조용하다. */
    private static final Duration WARN_INTERVAL = Duration.ofMinutes(5);

    /**
     * 한 바퀴에 쓸 수 있는 시간. 채팅 서버가 건강하면 50건에 1초도 안 걸리므로 평소에는
     * 걸리지 않고, 느리거나 죽었을 때만 바퀴를 끊는다.
     */
    private static final Duration ROUND_BUDGET = Duration.ofSeconds(20);

    /** 한 바퀴에 남기는 PURGE 지연 ERROR 줄 수 상한. 넘치면 다음 바퀴가 이어 받는다. */
    private static final int MAX_ALERTS_PER_ROUND = 20;

    private final ChatOutboxStore store;
    private final ChatServerClient client;
    private final ChatLinkProperties properties;
    private final ChatOutboxProperties outboxProperties;

    /** 연속 실패 수. 0 이면 마지막 바퀴가 성공했거나 보낼 것이 없었다는 뜻이다. */
    private int consecutiveFailures;

    private OffsetDateTime lastWarnAt;

    /** 401 은 원인이 하나(비밀값 불일치)라 따로 억제한다 — 전달 실패 경고와 섞이면 묻힌다. */
    private OffsetDateTime lastUnauthorizedWarnAt;

    /**
     * PURGE 지연을 행별로 마지막에 알린 시각. 같은 행을 5초마다 외치지 않으려는 것이다.
     *
     * <b>인메모리라 재기동하면 비워진다.</b> 그러면 아직 막혀 있는 행이 한 번 더 알려지는데,
     * 그쪽이 옳다 — 재기동은 "누가 손을 댔다"는 뜻이고, 그 뒤에도 안 풀렸다면 다시 말해
     * 줘야 한다. 지속 저장을 만들지 않은 이유이기도 하다.
     */
    private final Map<Long, OffsetDateTime> lastPurgeAlertAt = new HashMap<>();

    /**
     * {@code fixedDelay} 다. {@code fixedRate} 로 두면 한 바퀴가 5초를 넘길 때(채팅 서버가
     * 느린 경우가 정확히 그렇다) 다음 바퀴가 겹쳐 들어와 같은 행을 또 집어 간다.
     */
    @Scheduled(fixedDelay = 5000)
    public void dispatch() {
        if (!properties.canDeliver()) {
            // 기동 로그(ChatServerClient 생성자)에 이미 한 줄 남겼다. 여기서 또 찍으면
            // 5초마다 같은 말이 반복된다 — 설정은 기동 중에 바뀌지 않는다.
            return;
        }
        List<ChatOutboxMessage> batch = store.deliverable(now(), BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        OffsetDateTime deadline = now().plus(ROUND_BUDGET);
        int sent = 0;
        int failed = 0;
        int handled = 0;
        for (ChatOutboxMessage message : batch) {
            if (now().isAfter(deadline)) {
                break;
            }
            handled++;
            if (deliver(message)) {
                sent++;
            } else {
                failed++;
            }
        }
        if (failed > 0) {
            noteFailure(batch.size() - sent, handled);
            return;
        }
        noteSuccess(sent);
    }

    /** @return 전달됐으면 true. dead 로 끝난 것도 "전달 안 됨"이다. */
    private boolean deliver(ChatOutboxMessage message) {
        ChatEventResult result = client.send(message);
        OffsetDateTime now = now();
        switch (result.outcome()) {
            case DELIVERED -> {
                store.markDelivered(message.eventId(), now);
                return true;
            }
            case UNAUTHORIZED -> {
                // 설정 사고다. 값을 맞추면 그대로 풀리므로 재시도는 유지한다.
                store.markFailed(message.eventId(), now);
                noteUnauthorized(now);
            }
            case REJECTED -> {
                if (store.markRejected(message.eventId(), now)) {
                    noteDead(message, result);
                }
            }
            default -> store.markFailed(message.eventId(), now);
        }
        return false;
    }

    /**
     * 완료된 지 오래된 행을 치운다 (계약서 §10 "완료 후 7일").
     *
     * 전달 바퀴와 붙이지 않는다. 5초마다 삭제 쿼리를 던질 이유가 없고, 지울 것이 없는
     * 날에도 쿼리는 나간다. 한 시간에 한 번이면 7일이라는 기준에 견줘 충분히 촘촘하다.
     *
     * dead 행은 여기 걸리지 않는다 — 쿼리가 {@code deliveredAt} 을 보는데 그 칸이 비어 있다.
     * 원인을 조사하고 손으로 조치할 때까지 남겨 두려는 것이다.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void deleteOldDelivered() {
        int deleted = store.deleteDeliveredBefore(now().minus(RETENTION));
        if (deleted > 0) {
            log.info("전달 완료된 채팅 이벤트 {}건 정리", deleted);
        }
    }

    /**
     * 너무 오래 못 보낸 PURGE 를 알린다 (법률 점검 11절).
     *
     * <b>왜 이것만 따로 보는가.</b> 전달은 횟수 제한 없이 재시도하므로, 계속 실패해도
     * 시스템은 아무 말 없이 잘 도는 것처럼 보인다. 그런데 PURGE 가 안 나간다는 것은
     * <b>탈퇴한 사람의 대화가 채팅 서버에 그대로 있다</b>는 뜻이다 — 개인정보 보호법
     * 제21조가 "지체 없이" 파기하라고 한 그 자리다. ROOM_INVALIDATED 는 대상이 아니다:
     * 늦어 봐야 채팅 서버의 15초 캐시가 메워 준다.
     *
     * 1분마다 도는 것은 기준이 시간 단위라서다. 1분 안쪽의 늦음은 의미가 없고, 5초마다
     * 쿼리를 던질 이유도 없다.
     *
     * @return 이번에 남긴 ERROR 줄 수 (테스트가 보는 값)
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public int alertOverduePurges() {
        OffsetDateTime now = now();
        Duration threshold = outboxProperties.purgeAlertAfter();
        List<OverduePurge> overdue = store.overduePurges(now.minus(threshold));
        forgetSettledAlerts(overdue);
        if (overdue.isEmpty()) {
            return 0;
        }

        int alerted = 0;
        for (OverduePurge purge : overdue) {
            if (alerted >= MAX_ALERTS_PER_ROUND) {
                log.error("탈퇴 대화 삭제가 밀린 건이 {}건 더 있다 — 다음 점검에서 이어서 알린다",
                        overdue.size() - alerted);
                break;
            }
            if (!shouldAlert(purge.eventId(), now, threshold)) {
                continue;
            }
            lastPurgeAlertAt.put(purge.eventId(), now);
            alerted++;
            log.error("탈퇴 대화 삭제(PURGE)가 채팅 서버에 전달되지 않았다 — "
                            + "eventId={}, 경과 {}, 시도 {}회{}. "
                            + "채팅 서버 상태를 확인할 것(개인정보 보호법 제21조 '지체 없이')",
                    purge.eventId(), describe(Duration.between(purge.createdAt(), now)),
                    purge.attempts(),
                    purge.dead() ? ", 재시도 포기(dead) — 수동 조치 필요" : "");
        }
        return alerted;
    }

    /**
     * 경보 간격은 지연 기준과 같은 값을 쓴다 (기본 1시간). 따로 두면 설정이 둘이 되고,
     * "1시간 넘으면 알린다"는 말과 "얼마나 자주 알린다"가 어긋날 수 있다.
     */
    private boolean shouldAlert(Long eventId, OffsetDateTime now, Duration interval) {
        OffsetDateTime last = lastPurgeAlertAt.get(eventId);
        return last == null || !now.isBefore(last.plus(interval));
    }

    /** 해결된(전달됐거나 지워진) 행의 기억은 버린다 — 맵이 끝없이 자라지 않게. */
    private void forgetSettledAlerts(List<OverduePurge> overdue) {
        if (lastPurgeAlertAt.isEmpty()) {
            return;
        }
        Set<Long> stillOverdue = new HashSet<>();
        overdue.forEach(purge -> stillOverdue.add(purge.eventId()));
        lastPurgeAlertAt.keySet().retainAll(stillOverdue);
    }

    private void noteDead(ChatOutboxMessage message, ChatEventResult result) {
        boolean purge = message.type() == ChatEventType.PURGE;
        log.error("채팅 이벤트를 {}회 거절당해 더 보내지 않는다 — eventId={}, type={}, 응답 {}{}",
                ChatOutbox.DEAD_AFTER_ATTEMPTS, message.eventId(), message.type(),
                result.describeStatus(),
                purge ? ". PURGE 라 탈퇴 대화가 채팅 서버에 남아 있다 — 수동 조치 필요" : "");
    }

    private void noteUnauthorized(OffsetDateTime now) {
        if (lastUnauthorizedWarnAt != null
                && now.isBefore(lastUnauthorizedWarnAt.plus(WARN_INTERVAL))) {
            return;
        }
        lastUnauthorizedWarnAt = now;
        log.warn("채팅 서버가 공유 비밀을 거절했다(401) — 양쪽 CHAT_INTERNAL_SECRET 이 다르다. "
                + "값을 맞출 때까지 이벤트가 쌓인다");
    }

    private void noteFailure(int remaining, int handled) {
        consecutiveFailures++;
        OffsetDateTime now = now();
        boolean firstTime = consecutiveFailures == 1;
        boolean intervalPassed = lastWarnAt != null && !now.isBefore(lastWarnAt.plus(WARN_INTERVAL));
        if (firstTime || intervalPassed) {
            lastWarnAt = now;
            log.warn("채팅 서버에 이벤트를 보내지 못했다 — 미전송 {}건, 이번 바퀴 {}건 시도, "
                            + "연속 실패 {}회. 쌓아 두고 지수 백오프로 재시도한다",
                    remaining, handled, consecutiveFailures);
            return;
        }
        log.debug("채팅 이벤트 전달 실패 — 미전송 {}건, 연속 실패 {}회", remaining, consecutiveFailures);
    }

    private void noteSuccess(int sent) {
        if (consecutiveFailures > 0) {
            log.info("채팅 서버 전달 복구 — {}건 보냈다 (연속 실패 {}회 뒤)", sent, consecutiveFailures);
            consecutiveFailures = 0;
            lastWarnAt = null;
            lastUnauthorizedWarnAt = null;
            return;
        }
        log.debug("채팅 이벤트 {}건 전달", sent);
    }

    /** "1시간 12분" 처럼. 초 단위는 이 경보에서 의미가 없다. */
    private static String describe(Duration elapsed) {
        long hours = elapsed.toHours();
        long minutes = elapsed.toMinutesPart();
        return hours > 0 ? "%d시간 %d분".formatted(hours, minutes) : "%d분".formatted(minutes);
    }

    /** 테스트가 시간을 밀어 보려고 덮어쓰는 자리. */
    OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
