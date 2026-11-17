package com.kickoff.be.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.weather.client.KmaProvider;
import com.kickoff.be.weather.client.WeatherProperties;
import com.kickoff.be.weather.client.dto.DayTemperature;
import com.kickoff.be.weather.client.dto.HourSlot;
import com.kickoff.be.weather.client.dto.MidDay;
import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import com.kickoff.be.weather.dto.Weather;
import com.kickoff.be.weather.dto.WeatherCondition;
import com.kickoff.be.weather.dto.WeatherKind;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * 보관된 예보에서 응답에 실을 값을 고르는 규칙 (계약서 §8-3 「값을 정하는 규칙」).
 *
 * 테스트 전용 지역표를 쓴다 — 운영 표는 중기 코드가 아직 비어 있어 DAILY 경로를 밟을 수
 * 없다(weather-area.csv 머리말 참고).
 */
class WeatherServiceTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 11, 17, 9, 0, 0, 0, KST);
    /** 테스트 표의 "서울 강서구" 격자. */
    private static final String SEOUL_GANGSEO_GRID = "58,126";

    private WeatherAreas areas;
    private WeatherStore store;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        areas = new WeatherAreas("weather-area-test.csv");
        store = new WeatherStore();
        environment = new MockEnvironment();
        environment.setActiveProfiles("test");
    }

    // ── 단기예보 (HOURLY)

    @Test
    @DisplayName("경기 시각의 칸을 그대로 쓴다")
    void usesTheSlotOfTheMatchHour() {
        putShortTerm(NOW, Map.of(
                LocalDateTime.of(2026, 11, 18, 7, 0), new HourSlot(1, 0, 3, 20)));

        Weather weather = service().forMatch(match(11, 18, 7, 40), "서울 강서구", NOW);

        assertThat(weather.kind()).isEqualTo(WeatherKind.HOURLY);
        assertThat(weather.condition()).isEqualTo(WeatherCondition.CLEAR);
        assertThat(weather.temperature()).isEqualTo(3);
        assertThat(weather.precipitationProbability()).isEqualTo(20);
        assertThat(weather.area()).isEqualTo("서울 강서구");
        assertThat(weather.announcedAt()).isEqualTo(NOW);
        assertThat(weather.temperatureMin()).isNull();
        assertThat(weather.temperatureMax()).isNull();
    }

    /** 끝 구간은 3시간 간격이라 정시 칸이 없을 수 있다 (계약서 §8-3). */
    @Test
    @DisplayName("그 칸이 없으면 앞뒤 2시간 안에서 가장 가까운 칸을 쓴다")
    void fallsBackToNearestSlot() {
        putShortTerm(NOW, Map.of(
                LocalDateTime.of(2026, 11, 18, 6, 0), new HourSlot(4, 0, 1, 60)));

        Weather weather = service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW);

        assertThat(weather.condition()).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(weather.temperature()).isEqualTo(1);
    }

    /** 같은 거리면 앞 칸이다 — 경기 전의 하늘이 끝난 뒤의 하늘보다 쓸모 있다. */
    @Test
    @DisplayName("거리가 같으면 앞 칸을 쓴다")
    void prefersTheEarlierSlotOnATie() {
        Map<LocalDateTime, HourSlot> slots = new LinkedHashMap<>();
        slots.put(LocalDateTime.of(2026, 11, 18, 6, 0), new HourSlot(1, 0, 1, 0));
        slots.put(LocalDateTime.of(2026, 11, 18, 8, 0), new HourSlot(4, 0, 9, 0));
        putShortTerm(NOW, slots);

        Weather weather = service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW);

        assertThat(weather.temperature()).isEqualTo(1);
    }

    @Test
    @DisplayName("앞뒤 2시간 안에도 칸이 없으면 날씨가 없다")
    void noSlotWithinTwoHours() {
        putShortTerm(NOW, Map.of(
                LocalDateTime.of(2026, 11, 18, 4, 0), new HourSlot(1, 0, 1, 0)));

        assertThat(service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW)).isNull();
    }

    /** 낡은 예보를 최신인 것처럼 보이지 않는다 (계약서 §8-3 규칙 4). */
    @Test
    @DisplayName("단기는 발표 12시간이 지나면 내리지 않는다")
    void staleShortTermIsDropped() {
        putShortTerm(NOW.minusHours(13), Map.of(
                LocalDateTime.of(2026, 11, 18, 7, 0), new HourSlot(1, 0, 3, 20)));

        assertThat(service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW)).isNull();
    }

    @Test
    @DisplayName("12시간 안이면 호출이 실패했어도 직전 값을 계속 내린다")
    void freshEnoughShortTermIsKept() {
        putShortTerm(NOW.minusHours(11), Map.of(
                LocalDateTime.of(2026, 11, 18, 7, 0), new HourSlot(1, 0, 3, 20)));

        assertThat(service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW)).isNotNull();
    }

    // ── 중기예보 (DAILY)

    @Test
    @DisplayName("단기가 덮지 않는 날은 중기의 그날 값을 쓴다")
    void usesMidTermWhenShortTermIsAbsent() {
        LocalDate day = LocalDate.of(2026, 11, 24);
        store.putMidLand("TEST-LAND-SEOUL", new MidLandForecast(NOW,
                Map.of(day, new MidDay("맑음", "구름많고 비", 10, 60))));
        store.putMidTemperature("TEST-TA-SEOUL", new MidTemperature(NOW,
                Map.of(day, new DayTemperature(2, 9))));

        Weather morning = service().forMatch(match(11, 24, 7, 0), "서울 강서구", NOW);
        Weather afternoon = service().forMatch(match(11, 24, 15, 0), "서울 강서구", NOW);

        assertThat(morning.kind()).isEqualTo(WeatherKind.DAILY);
        assertThat(morning.condition()).isEqualTo(WeatherCondition.CLEAR);
        assertThat(morning.precipitationProbability()).isEqualTo(10);
        assertThat(morning.temperatureMin()).isEqualTo(2);
        assertThat(morning.temperatureMax()).isEqualTo(9);
        assertThat(morning.temperature()).as("DAILY 에는 시각 기온이 없다").isNull();

        assertThat(afternoon.condition()).as("12시부터는 오후 값")
                .isEqualTo(WeatherCondition.RAIN);
        assertThat(afternoon.precipitationProbability()).isEqualTo(60);
    }

    /** 단기와 중기가 같은 날을 덮으면 더 촘촘한 쪽이다 (계약서 §8-3 규칙 3). */
    @Test
    @DisplayName("같은 날을 둘 다 덮으면 단기가 먼저다")
    void shortTermWinsOverMidTerm() {
        LocalDate day = LocalDate.of(2026, 11, 20);
        putShortTerm(NOW, Map.of(
                LocalDateTime.of(2026, 11, 20, 7, 0), new HourSlot(1, 0, 3, 20)));
        store.putMidLand("TEST-LAND-SEOUL", new MidLandForecast(NOW,
                Map.of(day, new MidDay("흐림", "흐림", 80, 80))));

        Weather weather = service().forMatch(match(11, 20, 7, 0), "서울 강서구", NOW);

        assertThat(weather.kind()).isEqualTo(WeatherKind.HOURLY);
        assertThat(weather.condition()).isEqualTo(WeatherCondition.CLEAR);
    }

    /** 중기 쪽 해석이 실패해도 기온만 빠진다 — 개편 예고가 있어 한쪽만 바뀔 수 있다. */
    @Test
    @DisplayName("기온을 못 받았어도 중기 날씨는 내린다")
    void midTermWithoutTemperature() {
        LocalDate day = LocalDate.of(2026, 11, 24);
        store.putMidLand("TEST-LAND-SEOUL", new MidLandForecast(NOW,
                Map.of(day, new MidDay("흐림", "흐림", 70, 70))));

        Weather weather = service().forMatch(match(11, 24, 7, 0), "서울 강서구", NOW);

        assertThat(weather.condition()).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(weather.temperatureMin()).isNull();
        assertThat(weather.temperatureMax()).isNull();
    }

    /** 중기 코드가 비어 있는 지역은 중기 없이 단기만 나간다 (표가 채워질 때까지). */
    @Test
    @DisplayName("표에 중기 코드가 없는 지역은 중기를 내리지 않는다")
    void areaWithoutMidTermCodes() {
        store.putMidLand("TEST-LAND-SEOUL", new MidLandForecast(NOW,
                Map.of(LocalDate.of(2026, 11, 24), new MidDay("맑음", "맑음", 0, 0))));

        assertThat(service().forMatch(match(11, 24, 7, 0), "강원 춘천시", NOW)).isNull();
    }

    @Test
    @DisplayName("중기는 발표 36시간이 지나면 내리지 않는다")
    void staleMidTermIsDropped() {
        LocalDate day = LocalDate.of(2026, 11, 24);
        store.putMidLand("TEST-LAND-SEOUL", new MidLandForecast(NOW.minusHours(37),
                Map.of(day, new MidDay("맑음", "맑음", 0, 0))));

        assertThat(service().forMatch(match(11, 24, 7, 0), "서울 강서구", NOW)).isNull();
    }

    // ── 날씨가 없는 경우 (계약서 §8-3)

    @Test
    @DisplayName("지난 경기는 날씨가 없다")
    void pastMatchHasNoWeather() {
        putShortTerm(NOW, Map.of(
                LocalDateTime.of(2026, 11, 16, 7, 0), new HourSlot(1, 0, 3, 20)));

        assertThat(service().forMatch(match(11, 16, 7, 0), "서울 강서구", NOW)).isNull();
    }

    @Test
    @DisplayName("시/도를 알아볼 수 없는 지역은 날씨가 없다")
    void unknownRegionHasNoWeather() {
        assertThat(service().forMatch(match(11, 18, 7, 0), "어딘가 저기", NOW)).isNull();
        assertThat(service().forMatch(match(11, 18, 7, 0), null, NOW)).isNull();
    }

    @Test
    @DisplayName("보관된 예보가 없으면 날씨가 없다 — 예외가 아니다")
    void nothingHeldMeansNull() {
        assertThat(service().forMatch(match(11, 18, 7, 0), "서울 강서구", NOW)).isNull();
    }

    // ── 개발용 가짜 값

    /** 운영에 WEATHER_FAKE 가 섞여 들어와도 지어낸 예보가 나가면 안 된다 (기상법 제17조). */
    @Test
    @DisplayName("가짜 값은 dev 프로필이 아니면 무시한다")
    void fakeIsIgnoredOutsideDev() {
        assertThat(service(true).forMatch(match(11, 18, 7, 0), "서울 강서구", NOW)).isNull();
    }

    @Test
    @DisplayName("dev 에서는 기상청 없이도 값이 나오고, 같은 글은 늘 같은 값이다")
    void fakeInDev() {
        environment.setActiveProfiles("dev");
        WeatherService service = service(true);

        Weather first = service.forMatch(match(11, 18, 7, 0), "서울 강서구", NOW);
        Weather again = service.forMatch(match(11, 18, 7, 0), "서울 강서구", NOW);

        assertThat(first).isEqualTo(again);
        assertThat(first.kind()).as("오늘~3일은 HOURLY").isEqualTo(WeatherKind.HOURLY);
        assertThat(service.forMatch(match(11, 24, 7, 0), "서울 강서구", NOW).kind())
                .as("4~10일은 DAILY").isEqualTo(WeatherKind.DAILY);
        assertThat(service.forMatch(match(12, 20, 7, 0), "서울 강서구", NOW))
                .as("10일을 넘으면 없다").isNull();
    }

    // ── 헬퍼

    private WeatherService service() {
        return service(false);
    }

    private WeatherService service(boolean fake) {
        return new WeatherService(areas, store,
                new WeatherProperties("key", KmaProvider.DATA_GO_KR, null, fake), environment);
    }

    private void putShortTerm(OffsetDateTime announcedAt, Map<LocalDateTime, HourSlot> slots) {
        store.putShortTerm(SEOUL_GANGSEO_GRID, new ShortTermForecast(announcedAt, slots));
    }

    private static OffsetDateTime match(int month, int day, int hour, int minute) {
        return OffsetDateTime.of(2026, month, day, hour, minute, 0, 0, KST);
    }
}
