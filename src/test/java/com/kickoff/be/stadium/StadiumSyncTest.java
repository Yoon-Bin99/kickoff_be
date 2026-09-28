package com.kickoff.be.stadium;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.stadium.client.SeoulOpenDataProperties;
import com.kickoff.be.stadium.client.SeoulReservationClient;
import com.kickoff.be.stadium.client.SeoulReservationException;
import com.kickoff.be.stadium.client.SeoulReservationRow;
import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;
import com.kickoff.be.stadium.service.SeoulStadiumSyncService;
import com.kickoff.be.stadium.service.StadiumSyncWriter;
import com.kickoff.be.support.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClient;

/**
 * 서울 공공 구장 동기화 (계약서 §8-1, v1.27.0).
 *
 * <b>공공 API 를 실제로 부르지 않는다.</b> 클라이언트를 갈아 끼워 "무엇을 돌려줬을 때 DB 가
 * 어떻게 되는가"만 본다 — 여기서 검증할 것은 네트워크가 아니라 <b>실패했을 때 기존 데이터를
 * 지키는가</b>이고, 그건 상대 서버 상태와 무관하게 항상 같아야 한다.
 */
class StadiumSyncTest extends IntegrationTestSupport {

    @Autowired
    private StadiumSyncWriter writer;

    /** 공공 API 를 대신하는 클라이언트. 무엇을 돌려줄지(또는 던질지)를 테스트가 정한다. */
    private static class FakeClient extends SeoulReservationClient {

        private final boolean configured;
        private final List<SeoulReservationRow> rows;
        private final RuntimeException failure;

        private FakeClient(boolean configured, List<SeoulReservationRow> rows,
                           RuntimeException failure) {
            super(new SeoulOpenDataProperties(configured ? "test-key" : ""),
                    RestClient.builder());
            this.configured = configured;
            this.rows = rows;
            this.failure = failure;
        }

        static FakeClient returning(List<SeoulReservationRow> rows) {
            return new FakeClient(true, rows, null);
        }

        static FakeClient failing() {
            return new FakeClient(true, List.of(), new SeoulReservationException("일부러 실패"));
        }

        static FakeClient withoutKey() {
            return new FakeClient(false, List.of(), null);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<SeoulReservationRow> fetchAll() {
            if (failure != null) {
                throw failure;
            }
            return rows;
        }
    }

    private SeoulStadiumSyncService syncWith(FakeClient client) {
        return new SeoulStadiumSyncService(client, writer);
    }

    private SeoulReservationRow row(String id, String name, String area, String status) {
        return new SeoulReservationRow(id, name, area, "축구장", status,
                "https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id=" + id,
                "2026-09-01 00:00:00.0", "2026-12-31 00:00:00.0");
    }

    @Test
    @DisplayName("동기화가 공공 구장을 넣는다 — 지역에 시 이름을 붙이고 이용 기간은 날짜만 남긴다")
    void insertsPublicStadiums() {
        syncWith(FakeClient.returning(List.of(
                row("S1", "월곡 인조잔디구장", "성북구", "접수중")))).sync();

        Stadium saved = stadiumRepository.findByExternalId("S1").orElseThrow();
        assertThat(saved.getName()).isEqualTo("월곡 인조잔디구장");
        // AREANM 은 "성북구"만 온다. 여기서 "서울"을 붙이지 않으면 지역 필터 "서울"이
        // 공공 구장을 하나도 못 찾는다 (프리픽스 매칭이라 더 그렇다).
        assertThat(saved.getRegion()).isEqualTo("서울 성북구");
        assertThat(saved.getSource()).isEqualTo(StadiumSource.SEOUL_PUBLIC);
        assertThat(saved.getAcceptStatus()).isEqualTo("접수중");
        assertThat(saved.getUsePeriod()).isEqualTo("2026-09-01 ~ 2026-12-31");
        // 공공 API 응답에 주소 필드가 없다 — 지어내지 않고 비운다.
        assertThat(saved.getAddress()).isNull();
    }

    @Test
    @DisplayName("같은 SVCID 는 새로 넣지 않고 갱신한다 — id 가 유지돼야 한다")
    void updatesInsteadOfDuplicating() {
        syncWith(FakeClient.returning(List.of(row("S1", "월곡 구장", "성북구", "접수중")))).sync();
        Long firstId = stadiumRepository.findByExternalId("S1").orElseThrow().getId();

        syncWith(FakeClient.returning(List.of(row("S1", "월곡 구장", "성북구", "예약마감")))).sync();

        assertThat(stadiumRepository.countBySource(StadiumSource.SEOUL_PUBLIC)).isEqualTo(1);
        Stadium after = stadiumRepository.findByExternalId("S1").orElseThrow();
        assertThat(after.getId()).isEqualTo(firstId);
        assertThat(after.getAcceptStatus()).isEqualTo("예약마감");
    }

    @Test
    @DisplayName("공공 API 가 실패하면 기존 데이터를 그대로 둔다")
    void keepsDataWhenFetchFails() {
        syncWith(FakeClient.returning(List.of(row("S1", "월곡 구장", "성북구", "접수중")))).sync();

        int applied = syncWith(FakeClient.failing()).sync();

        assertThat(applied).isZero();
        assertThat(stadiumRepository.findByExternalId("S1")).isPresent();
    }

    /**
     * 0건도 실패로 본다. 서울에 축구장·풋살장 예약이 0건일 리 없으므로, 0건은 응답 형식이
     * 바뀌었다는 신호다 — 그대로 반영하면 목록이 통째로 비고 화면은 그걸 정상처럼 보여 준다.
     */
    @Test
    @DisplayName("결과가 0건이면 덮어쓰지 않는다")
    void keepsDataWhenResultIsEmpty() {
        syncWith(FakeClient.returning(List.of(row("S1", "월곡 구장", "성북구", "접수중")))).sync();

        int applied = syncWith(FakeClient.returning(List.of())).sync();

        assertThat(applied).isZero();
        assertThat(stadiumRepository.findByExternalId("S1")).isPresent();
    }

    @Test
    @DisplayName("인증키가 없으면 조용히 건너뛴다 — 수동 시드만으로 돌아가야 한다")
    void skipsWithoutKey() {
        stadiumRepository.save(manualStadium());

        int applied = syncWith(FakeClient.withoutKey()).sync();

        assertThat(applied).isZero();
        assertThat(stadiumRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("공공 API 에서 사라진 구장은 지우되 수동 시드는 건드리지 않는다")
    void removesGoneButKeepsManual() {
        stadiumRepository.save(manualStadium());
        syncWith(FakeClient.returning(List.of(
                row("S1", "월곡 구장", "성북구", "접수중"),
                row("S2", "강서 구장", "강서구", "접수중")))).sync();

        syncWith(FakeClient.returning(List.of(row("S1", "월곡 구장", "성북구", "접수중")))).sync();

        assertThat(stadiumRepository.findByExternalId("S2")).isEmpty();
        assertThat(stadiumRepository.countBySource(StadiumSource.MANUAL)).isEqualTo(1);
    }

    private Stadium manualStadium() {
        return Stadium.builder()
                .name("탄천변축구장A")
                .region("경기 성남시")
                .address("경기 성남시 중원구 여수동 7-17")
                .reservationUrl("https://res.isdc.co.kr/facilityList.do?facType=28")
                .source(StadiumSource.MANUAL)
                .build();
    }
}
