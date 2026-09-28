package com.kickoff.be.stadium.service;

import com.kickoff.be.stadium.entity.StadiumSource;
import com.kickoff.be.stadium.repository.StadiumRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 구장 동기화를 <b>언제</b> 돌릴지 (계약서 §8-1, v1.27.0) — 하루 1회 + 기동 시 비어 있으면 1회.
 *
 * <b>기동을 막지 않는다.</b> {@code ApplicationReadyEvent} 는 컨텍스트가 다 뜬 뒤에 오지만
 * 그대로 두면 리스너가 끝날 때까지 기동 로그가 멈추고, 그 사이 공공 API 가 느리면 헬스체크가
 * 늦게 초록으로 바뀐다 — 레일웨이에서는 그게 배포 실패로 보일 수 있다. {@code @Async} 로
 * 떼어 둔다.
 *
 * <b>요청 경로에는 아예 없다.</b> 동기화를 부르는 곳은 이 클래스뿐이다.
 *
 * <b>test 프로파일에서는 뜨지 않는다.</b> {@code SchedulingConfig} 가 스케줄러를 끄는 것만으로는
 * 부족하다 — {@code @EventListener} 는 스케줄러와 무관하게 컨텍스트가 뜰 때마다 불린다.
 * 그대로 두면 통합 테스트가 컨텍스트를 띄울 때마다 외부 API 를 찌른다(키가 없으면 조용히
 * 건너뛰긴 하지만, 그 "조용히"에 기대는 테스트는 키를 넣는 날 깨진다).
 * 동기화 로직 자체는 {@code SeoulStadiumSyncService} 를 직접 불러서 검증한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StadiumSyncScheduler {

    private final SeoulStadiumSyncService syncService;
    private final StadiumRepository stadiumRepository;

    /**
     * 새벽 5시 (계약서 §8-1 "하루 1회").
     *
     * 보관 기간 파기(4시 30분)와 겹치지 않게 둔다. 둘 다 무거운 작업은 아니지만, 같은 시각에
     * 돌면 로그가 섞여 어느 쪽이 느렸는지 보기 어렵다.
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void daily() {
        syncService.sync();
    }

    /**
     * 기동 시 <b>비어 있을 때만</b> 한 번 (계약서 §8-1).
     *
     * 이미 데이터가 있으면 돌지 않는다. 배포가 잦은 날 재기동마다 공공 API 를 찌르면 우리
     * 쿼터만 쓰고 얻는 게 없다 — 하루 1회 스케줄이 이미 최신을 맡는다.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (stadiumRepository.countBySource(StadiumSource.SEOUL_PUBLIC) > 0) {
            return;
        }
        // debug 로 남긴다 — 실제로 동기화가 돌았는지는 sync() 가 info 로 알린다.
        // 여기서 info 를 내면 인증키가 없어 건너뛴 날에도 "동기화한다"만 찍혀 오해를 준다.
        log.debug("서울 공공 구장이 비어 있어 기동 시 1회 동기화를 시도한다");
        syncService.sync();
    }
}
