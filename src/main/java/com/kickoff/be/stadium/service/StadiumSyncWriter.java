package com.kickoff.be.stadium.service;

import com.kickoff.be.stadium.client.SeoulReservationRow;
import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;
import com.kickoff.be.stadium.repository.StadiumRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동기화 결과를 DB 에 반영한다 (계약서 §8-1, v1.27.0).
 *
 * <b>{@link SeoulStadiumSyncService} 와 빈을 나눈 이유는 트랜잭션 경계 때문이다.</b> 같은
 * 클래스 안의 메서드를 직접 부르면 프록시를 타지 않아 {@code @Transactional} 이 조용히
 * 무시된다 — 예외가 나도 롤백이 안 되고, 로그에도 아무 흔적이 없다. 그리고 경계를 바깥으로
 * 넓혀 {@code sync()} 전체를 트랜잭션으로 묶으면 <b>외부 HTTP 호출이 트랜잭션 안에서</b>
 * 일어나 공공 API 가 느린 날 DB 커넥션을 그만큼 붙잡는다. 둘 다 피하려면 나누는 게 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StadiumSyncWriter {

    private final StadiumRepository stadiumRepository;

    /**
     * 받아 온 행을 반영한다. 호출자는 <b>전부 받아 오는 데 성공했을 때만</b> 여기로 온다.
     *
     * <b>사라진 것은 지운다.</b> 예약 서비스가 내려갔는데 목록에 남아 있으면 사용자가 죽은
     * 링크를 누른다. 일부만 받은 상태로는 여기 오지 않으므로 통째로 지워질 일은 없다.
     *
     * <b>MANUAL 시드는 건드리지 않는다</b> — 조회도 삭제도 SEOUL_PUBLIC 만 대상으로 한다.
     */
    @Transactional
    public int apply(List<SeoulReservationRow> rows) {
        Map<String, Stadium> existing = new HashMap<>();
        for (Stadium stadium : stadiumRepository.findAllBySource(StadiumSource.SEOUL_PUBLIC)) {
            existing.put(stadium.getExternalId(), stadium);
        }

        List<Stadium> created = new ArrayList<>();
        int applied = 0;
        for (SeoulReservationRow row : rows) {
            if (row.svcId() == null) {
                // 갱신 기준이 없는 행은 다음 동기화 때 중복으로 다시 들어온다. 버린다.
                continue;
            }
            applied++;
            Stadium found = existing.remove(row.svcId());
            if (found != null) {
                found.syncFrom(StadiumFields.name(row), StadiumFields.region(row), null,
                        StadiumFields.url(row), row.statusName(), StadiumFields.usePeriod(row));
            } else {
                created.add(Stadium.builder()
                        .externalId(row.svcId())
                        .name(StadiumFields.name(row))
                        .region(StadiumFields.region(row))
                        .reservationUrl(StadiumFields.url(row))
                        .source(StadiumSource.SEOUL_PUBLIC)
                        .acceptStatus(row.statusName())
                        .usePeriod(StadiumFields.usePeriod(row))
                        .build());
            }
        }
        stadiumRepository.saveAll(created);
        if (!existing.isEmpty()) {
            log.info("서울 구장 {}건이 공공 API 에서 사라져 삭제한다", existing.size());
            stadiumRepository.deleteAll(existing.values());
        }
        return applied;
    }
}
