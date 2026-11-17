package com.kickoff.be.weather.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 예보를 받아 오는 때를 정한다 (계약서 §8-3).
 *
 * 세 가지 계기가 있다.
 * <ol>
 *   <li>30분마다 — 새 발표가 나왔는데 아직 못 받은 곳을 메운다.</li>
 *   <li>기동 직후 한 번 — 보관은 메모리라 재기동하면 비어 있다. 요청을 막지 않게
 *       비동기로 돈다.</li>
 *   <li>글이 생기거나 날짜·지역이 바뀌었을 때 — 그 지역만 바로 받는다.</li>
 * </ol>
 *
 * <b>어떤 예외도 밖으로 내지 않는다.</b> 주기 작업에서 예외가 올라가면 스케줄러가
 * 그 작업을 더 돌리지 않는다 — 날씨 하나 때문에 다음 주기가 영영 오지 않으면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherScheduler {

    /** 30분 (계약서 §8-3). 발표 간격(단기 3시간)보다 촘촘해야 발표 직후에 메워진다. */
    private static final long INTERVAL_MS = 30 * 60 * 1000L;

    private final WeatherFetcher fetcher;

    @Scheduled(fixedDelay = INTERVAL_MS)
    public void refresh() {
        run(() -> fetcher.refreshAll(OffsetDateTime.now()), "주기");
    }

    /**
     * 기동 직후 한 번. {@code @Async} 라 기동을 붙잡지 않는다 — 기상청이 느린 날
     * 서버가 뜨지 못하면 날씨와 상관없는 API 까지 함께 멈춘다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run(() -> fetcher.refreshAll(OffsetDateTime.now()), "기동");
    }

    /**
     * 새 글·수정된 글의 지역. 커밋 뒤에 비동기로 받는다 — 그 요청의 응답은
     * {@code weather: null} 일 수 있다(계약서 §8-3).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRegion(WeatherRegionEvent event) {
        run(() -> fetcher.refreshRegion(event.region(), OffsetDateTime.now()), "글");
    }

    private void run(Runnable task, String trigger) {
        try {
            task.run();
        } catch (RuntimeException e) {
            log.warn("날씨 받아 오기 실패 ({}) — {}", trigger, e.getMessage());
        }
    }
}
