package com.kickoff.be.holiday.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.holiday.client.HolidayLookupException;
import com.kickoff.be.holiday.client.HolidayProperties;
import com.kickoff.be.holiday.client.KakaoHolidayClient;
import com.kickoff.be.holiday.dto.Holiday;
import com.kickoff.be.holiday.dto.HolidayResponse;
import com.kickoff.be.holiday.dto.HolidaySource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 공휴일 조회의 캐시와 실패 처리 (계약서 §8-2).
 *
 * <b>카카오를 실제로 부르지 않는다.</b> 클라이언트를 갈아 끼워 "무엇을 돌려줬을 때
 * 응답과 호출 횟수가 어떻게 되는가"만 본다 — 여기서 검증할 것은 네트워크가 아니라
 * <b>실패해도 200 을 내는가</b>와 <b>같은 해를 두 번 묻지 않는가</b>다.
 *
 * 스프링 컨텍스트를 띄우지 않는다. 캐시와 분기는 전부 이 클래스 안에 있다.
 */
class HolidayServiceTest {

    private static final int THIS_YEAR = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();

    private static final List<Holiday> ONE_DAY =
            List.of(new Holiday(LocalDate.of(2026, 1, 1), "1월1일"));

    /** 호출 횟수를 세고, 무엇을 돌려줄지(또는 던질지)를 테스트가 정한다. */
    private static class FakeClient extends KakaoHolidayClient {

        private final boolean configured;
        private final List<Holiday> holidays;
        private final RuntimeException failure;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeClient(boolean configured, List<Holiday> holidays, RuntimeException failure) {
            super(new HolidayProperties(configured ? "test-admin-key" : ""));
            this.configured = configured;
            this.holidays = holidays;
            this.failure = failure;
        }

        static FakeClient returning(List<Holiday> holidays) {
            return new FakeClient(true, holidays, null);
        }

        static FakeClient failing(RuntimeException failure) {
            return new FakeClient(true, List.of(), failure);
        }

        static FakeClient withoutKey() {
            return new FakeClient(false, List.of(), null);
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public List<Holiday> fetch(int year) {
            calls.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return holidays;
        }
    }

    @Test
    @DisplayName("받아 오면 source KAKAO")
    void successIsKakao() {
        HolidayResponse response = serviceOf(FakeClient.returning(ONE_DAY), Clock.systemUTC())
                .of(2026);

        assertThat(response.year()).isEqualTo(2026);
        assertThat(response.source()).isEqualTo(HolidaySource.KAKAO);
        assertThat(response.holidays()).hasSize(1);
    }

    /** 키가 없으면 외부를 부르지도 않는다. 사용자가 콘솔에서 받아 넣는 값이라 정상 상태다. */
    @Test
    @DisplayName("키가 없으면 NONE — 외부를 부르지 않는다")
    void withoutKeyIsNone() {
        FakeClient client = FakeClient.withoutKey();

        HolidayResponse response = serviceOf(client, Clock.systemUTC()).of(2026);

        assertThat(response.source()).isEqualTo(HolidaySource.NONE);
        assertThat(response.holidays()).isEmpty();
        assertThat(client.calls).hasValue(0);
    }

    /**
     * 타임아웃·오류 응답이 NONE 이 되어야 한다. <b>500 이 나가면 안 된다</b>(계약서 §8-2) —
     * 달력은 앱의 기본 화면이라, 공휴일을 몰라서 화면이 안 뜨는 쪽이 훨씬 나쁘다.
     */
    @Test
    @DisplayName("호출이 실패하면 NONE — 예외가 밖으로 나가지 않는다")
    void failureIsNone() {
        HolidayResponse response = serviceOf(
                FakeClient.failing(new HolidayLookupException("타임아웃")), Clock.systemUTC())
                .of(2026);

        assertThat(response.source()).isEqualTo(HolidaySource.NONE);
        assertThat(response.holidays()).isEmpty();
    }

    /**
     * 카카오에 그 해 데이터가 없으면 0건이 온다. 그때 <b>KAKAO 0건이 아니라 NONE</b> 이어야 한다 —
     * FE 가 둘을 구별해 폴백할지 정한다. KAKAO 0건으로 내리면 FE 는 "공휴일이 없는 해"로
     * 믿고 계산값을 쓰지 않는다.
     */
    @Test
    @DisplayName("0건은 KAKAO 가 아니라 NONE — 카카오가 그 해를 모른다는 뜻이다")
    void emptyResultIsNone() {
        HolidayResponse response = serviceOf(FakeClient.returning(List.of()), Clock.systemUTC())
                .of(2030);

        assertThat(response.source()).isEqualTo(HolidaySource.NONE);
    }

    @Test
    @DisplayName("같은 해를 두 번 물으면 외부는 한 번만 부른다")
    void cacheHit() {
        FakeClient client = FakeClient.returning(ONE_DAY);
        HolidayService service = serviceOf(client, Clock.systemUTC());

        service.of(2026);
        service.of(2026);
        service.of(2026);

        assertThat(client.calls).hasValue(1);
    }

    @Test
    @DisplayName("다른 해는 따로 부른다")
    void cacheIsPerYear() {
        FakeClient client = FakeClient.returning(ONE_DAY);
        HolidayService service = serviceOf(client, Clock.systemUTC());

        service.of(2026);
        service.of(2027);

        assertThat(client.calls).hasValue(2);
    }

    /**
     * 올해·앞으로의 해는 하루 뒤 다시 묻는다. 임시공휴일이 중간에 지정되는 일이 실제로
     * 있어서, 영구히 담아 두면 그해 달력이 끝까지 틀린다.
     */
    @Test
    @DisplayName("올해 데이터는 하루 뒤 다시 부른다")
    void futureYearExpiresAfterOneDay() {
        FakeClient client = FakeClient.returning(ONE_DAY);
        MovableClock clock = new MovableClock(Instant.parse("2026-06-01T00:00:00Z"));
        HolidayService service = serviceOf(client, clock);

        service.of(THIS_YEAR);
        clock.advance(Duration.ofHours(23));
        service.of(THIS_YEAR);
        assertThat(client.calls).as("하루가 지나기 전엔 캐시").hasValue(1);

        clock.advance(Duration.ofHours(2));
        service.of(THIS_YEAR);
        assertThat(client.calls).as("하루가 지나면 다시 묻는다").hasValue(2);
    }

    /** 이미 끝난 해의 공휴일은 바뀌지 않는다. 영구히 담아 둔다. */
    @Test
    @DisplayName("지난 해는 하루가 지나도 다시 부르지 않는다")
    void pastYearIsCachedForever() {
        FakeClient client = FakeClient.returning(ONE_DAY);
        MovableClock clock = new MovableClock(Instant.parse("2026-06-01T00:00:00Z"));
        HolidayService service = serviceOf(client, clock);

        service.of(THIS_YEAR - 1);
        clock.advance(Duration.ofDays(400));
        service.of(THIS_YEAR - 1);

        assertThat(client.calls).hasValue(1);
    }

    /**
     * NONE 은 짧게만 담는다. 영구로 두면 키를 넣거나 공표된 뒤에도 모른 채 남고, 아예
     * 담지 않으면 카카오가 죽은 동안 <b>모든 요청이 타임아웃만큼 기다린다</b>.
     */
    @Test
    @DisplayName("NONE 은 10분만 담는다 — 복구가 빨라야 한다")
    void noneIsCachedBriefly() {
        FakeClient client = FakeClient.failing(new HolidayLookupException("죽었다"));
        MovableClock clock = new MovableClock(Instant.parse("2026-06-01T00:00:00Z"));
        HolidayService service = serviceOf(client, clock);

        service.of(2026);
        clock.advance(Duration.ofMinutes(5));
        service.of(2026);
        assertThat(client.calls).as("5분 안에는 다시 찌르지 않는다").hasValue(1);

        clock.advance(Duration.ofMinutes(6));
        service.of(2026);
        assertThat(client.calls).as("10분이 지나면 다시 시도한다").hasValue(2);
    }

    /**
     * 같은 패키지에 있어서 package-private 생성자가 그대로 보인다. 운영 코드의 가시성을
     * 넓히지 않으려고 테스트를 이 패키지에 뒀다 — OAuthExchangeCodeStore 때와 같은 판단이다.
     */
    private HolidayService serviceOf(KakaoHolidayClient client, Clock clock) {
        return new HolidayService(client, clock);
    }

    /** TTL 경과를 만들려면 시간을 움직일 수 있어야 한다. */
    private static final class MovableClock extends Clock {

        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Seoul");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
