package com.kickoff.be.chatlink.service;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import com.kickoff.be.chatlink.client.ChatServerClient;
import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
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
 * <b>인스턴스가 하나라는 전제다.</b> 여럿이면 같은 행을 동시에 집어 같은 이벤트가 두 번
 * 나갈 수 있다 — 처리가 멱등이라 결과는 같지만 쓸모없는 일이다. 늘릴 때는 행 잠금
 * ({@code select ... for update skip locked})이 필요하다. README 에 전제로 적어 뒀다.
 *
 * <b>로그를 도배하지 않는다.</b> 채팅 서버가 꺼져 있으면 5초마다 실패하는데, 그걸 그대로
 * warn 으로 찍으면 하루에 만 줄이 넘고 <b>다른 경고가 그 안에 묻힌다.</b> 그래서 끊긴
 * 순간과 복구된 순간만 알리고, 그사이에는 {@link #WARN_INTERVAL} 마다 한 줄만 남긴다.
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

    private final ChatOutboxStore store;
    private final ChatServerClient client;
    private final ChatLinkProperties properties;

    /** 연속 실패 수. 0 이면 마지막 바퀴가 성공했거나 보낼 것이 없었다는 뜻이다. */
    private int consecutiveFailures;

    private OffsetDateTime lastWarnAt;

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
        int sent = 0;
        for (ChatOutboxMessage message : batch) {
            if (client.send(message)) {
                store.markDelivered(message.eventId(), now());
                sent++;
                continue;
            }
            store.markFailed(message.eventId(), now());
            // 한 건이 실패하면 보통 채팅 서버 자체가 안 뜬 것이다. 남은 것까지 줄줄이
            // 때려 봐야 타임아웃만 쌓이므로 이 바퀴는 여기서 접는다 — 남은 행은 그대로
            // 미전송이라 다음 바퀴에 다시 집힌다.
            noteFailure(batch.size() - sent);
            return;
        }
        noteSuccess(sent);
    }

    /**
     * 완료된 지 오래된 행을 치운다 (계약서 §10 "완료 후 7일").
     *
     * 전달 바퀴와 붙이지 않는다. 5초마다 삭제 쿼리를 던질 이유가 없고, 지울 것이 없는
     * 날에도 쿼리는 나간다. 한 시간에 한 번이면 7일이라는 기준에 견줘 충분히 촘촘하다.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void deleteOldDelivered() {
        int deleted = store.deleteDeliveredBefore(now().minus(RETENTION));
        if (deleted > 0) {
            log.info("전달 완료된 채팅 이벤트 {}건 정리", deleted);
        }
    }

    private void noteFailure(int remaining) {
        consecutiveFailures++;
        OffsetDateTime now = now();
        boolean firstTime = consecutiveFailures == 1;
        boolean intervalPassed = lastWarnAt != null && !now.isBefore(lastWarnAt.plus(WARN_INTERVAL));
        if (firstTime || intervalPassed) {
            lastWarnAt = now;
            log.warn("채팅 서버에 이벤트를 보내지 못했다 — 미전송 {}건, 연속 실패 {}회. "
                            + "쌓아 두고 지수 백오프로 재시도한다",
                    remaining, consecutiveFailures);
            return;
        }
        log.debug("채팅 이벤트 전달 실패 — 미전송 {}건, 연속 실패 {}회", remaining, consecutiveFailures);
    }

    private void noteSuccess(int sent) {
        if (consecutiveFailures > 0) {
            log.info("채팅 서버 전달 복구 — {}건 보냈다 (연속 실패 {}회 뒤)", sent, consecutiveFailures);
            consecutiveFailures = 0;
            lastWarnAt = null;
            return;
        }
        log.debug("채팅 이벤트 {}건 전달", sent);
    }

    /** 테스트가 시간을 밀어 보려고 덮어쓰는 자리. */
    OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
