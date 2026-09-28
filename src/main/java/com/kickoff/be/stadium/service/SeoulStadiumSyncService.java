package com.kickoff.be.stadium.service;

import com.kickoff.be.stadium.client.SeoulReservationClient;
import com.kickoff.be.stadium.client.SeoulReservationException;
import com.kickoff.be.stadium.client.SeoulReservationRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 서울 공공 구장 동기화 (계약서 §8-1, v1.27.0).
 *
 * <b>실패해도 마지막 성공 데이터를 덮지 않는다.</b> 계약서가 그렇게 정했고, 이유는 실패가
 * 조용하기 때문이다 — 목록이 비어도 200 이라 화면은 "구장이 없습니다"를 정상처럼 보여 준다.
 * 그래서 여기서는 세 가지를 모두 실패로 본다: 호출 예외, <b>0건</b>, 인증키 없음. 셋 다
 * DB 를 건드리지 않고 돌아간다.
 *
 * <b>트랜잭션은 여기에 없다</b> — 외부 호출을 트랜잭션 안에 두지 않으려고 반영을
 * {@link StadiumSyncWriter} 로 나눴다(그쪽 주석에 이유를 적었다).
 *
 * 언제 도는지는 {@link StadiumSyncScheduler} 가 정한다. 이 클래스는 <b>불리면 도는</b>
 * 것만 안다 — 그래야 테스트가 스케줄 없이 직접 부를 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeoulStadiumSyncService {

    private final SeoulReservationClient client;
    private final StadiumSyncWriter writer;

    /**
     * @return 반영한 구장 수. 건너뛰었거나 실패하면 0.
     */
    public int sync() {
        if (!client.isConfigured()) {
            // 키는 사용자가 신청해야 하는 것이라, 없는 상태가 정상 동작의 하나다.
            // warn 이 아니라 debug 로 남긴다 — 매일 경고가 뜨면 진짜 경고가 묻힌다.
            log.debug("서울 구장 동기화 건너뜀 — SEOUL_OPENDATA_KEY 없음 (수동 시드만 노출)");
            return 0;
        }
        List<SeoulReservationRow> rows;
        try {
            rows = client.fetchAll();
        } catch (SeoulReservationException e) {
            log.warn("서울 구장 동기화 실패 — 기존 데이터 유지. {}", e.getMessage());
            return 0;
        }
        if (rows.isEmpty()) {
            // 서울에 축구장·풋살장 예약 서비스가 0건일 리 없다. 0건은 상대 응답이 바뀌었다는
            // 신호로 본다 — 그대로 반영하면 목록이 통째로 빈다.
            log.warn("서울 구장 동기화 결과가 0건 — 응답 형식이 바뀌었을 수 있다. 기존 데이터 유지");
            return 0;
        }
        int applied = writer.apply(rows);
        log.info("서울 구장 동기화 완료 — {}건", applied);
        return applied;
    }
}
