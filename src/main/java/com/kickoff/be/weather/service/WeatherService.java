package com.kickoff.be.weather.service;

import com.kickoff.be.weather.client.WeatherProperties;
import com.kickoff.be.weather.client.dto.DayTemperature;
import com.kickoff.be.weather.client.dto.HourSlot;
import com.kickoff.be.weather.client.dto.MidDay;
import com.kickoff.be.weather.client.dto.MidLandForecast;
import com.kickoff.be.weather.client.dto.MidTemperature;
import com.kickoff.be.weather.client.dto.ShortTermForecast;
import com.kickoff.be.weather.dto.Weather;
import com.kickoff.be.weather.dto.WeatherCondition;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 글·신청 응답에 실을 날씨를 만든다 (계약서 §8-3).
 *
 * <b>여기서 기상청을 부르지 않는다.</b> 보관된 예보만 읽는다 — 목록 한 번에 글이 스무
 * 개면 바깥 호출이 스무 번 붙고, 기상청이 느린 날 홈 화면이 통째로 느려진다. 날씨가
 * 빠지는 쪽이 화면이 안 뜨는 쪽보다 낫다(§8-2 공휴일과 같은 생각).
 *
 * <b>없으면 null 이다.</b> 예외를 던지지 않는다 — 날씨 하나 때문에 글 조회가 500 이 되면
 * 안 된다.
 */
@Service
@RequiredArgsConstructor
public class WeatherService {

    /** 그 칸이 없을 때 앞뒤로 찾아보는 범위 (계약서 §8-3) — 끝 구간은 3시간 간격이다. */
    private static final int NEAREST_SLOT_HOURS = 2;

    /** 오전·오후가 갈리는 기준 (계약서 §8-3). */
    private static final int NOON = 12;

    private final WeatherAreas areas;
    private final WeatherStore store;
    private final WeatherProperties properties;
    private final Environment environment;

    /**
     * 경기 시각·지역의 예상 날씨. 없으면 null 이다.
     *
     * 지난 경기는 보지 않는다 — 예보가 아니라 지난 일이고, 계약서가 null 로 못박았다.
     */
    public Weather forMatch(OffsetDateTime matchAt, String region) {
        return forMatch(matchAt, region, OffsetDateTime.now());
    }

    Weather forMatch(OffsetDateTime matchAt, String region, OffsetDateTime now) {
        if (matchAt == null || !matchAt.isAfter(now)) {
            return null;
        }
        WeatherArea area = areas.find(region).orElse(null);
        if (area == null) {
            return null;
        }
        if (useFake()) {
            return FakeWeather.of(matchAt, area, now);
        }
        Weather hourly = hourly(matchAt, area, now);
        // 단기가 먼저다 (계약서 §8-3 규칙 3) — 같은 날을 둘 다 덮으면 더 촘촘한 쪽을 쓴다.
        return hourly != null ? hourly : daily(matchAt, area, now);
    }

    /**
     * 가짜 값은 <b>dev 프로필에서만</b> 듣는다 (계약서 §8-3).
     *
     * 설정 하나만 보고 켜면, 운영에 그 환경변수가 섞여 들어온 날 지어낸 예보가 기상청
     * 예보인 것처럼 나간다. 프로필까지 함께 봐야 그 사고가 구조적으로 막힌다.
     */
    private boolean useFake() {
        return properties.fake() && environment.matchesProfiles("dev");
    }

    private Weather hourly(OffsetDateTime matchAt, WeatherArea area, OffsetDateTime now) {
        Optional<ShortTermForecast> held = store.shortTerm(area.gridKey(), now);
        if (held.isEmpty()) {
            return null;
        }
        ShortTermForecast forecast = held.get();
        LocalDateTime at = matchAt.atZoneSameInstant(WeatherAnnouncements.KST)
                .toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
        HourSlot slot = nearestSlot(forecast.slots(), at);
        if (slot == null) {
            return null;
        }
        return Weather.hourly(
                WeatherConditions.ofShortTerm(slot.pty(), slot.sky()),
                slot.temperature(),
                slot.precipitationProbability(),
                area.displayName(),
                forecast.announcedAt());
    }

    /**
     * 정시 칸이 없으면 앞뒤 2시간 안에서 가장 가까운 칸을 쓴다 (계약서 §8-3).
     * 거리가 같으면 앞 칸이다 — 경기 시작 전의 하늘이 끝난 뒤의 하늘보다 쓸모 있다.
     */
    private HourSlot nearestSlot(Map<LocalDateTime, HourSlot> slots, LocalDateTime at) {
        HourSlot exact = slots.get(at);
        if (exact != null) {
            return exact;
        }
        for (int distance = 1; distance <= NEAREST_SLOT_HOURS; distance++) {
            HourSlot before = slots.get(at.minusHours(distance));
            if (before != null) {
                return before;
            }
            HourSlot after = slots.get(at.plusHours(distance));
            if (after != null) {
                return after;
            }
        }
        return null;
    }

    private Weather daily(OffsetDateTime matchAt, WeatherArea area, OffsetDateTime now) {
        if (!area.hasMidTerm()) {
            // 표의 중기 칸이 비어 있는 지역이다 — 단기만 나간다 (weather-area.csv 머리말).
            return null;
        }
        Optional<MidLandForecast> land = store.midLand(area.midLandRegId(), now);
        if (land.isEmpty()) {
            return null;
        }
        java.time.ZonedDateTime kst = matchAt.atZoneSameInstant(WeatherAnnouncements.KST);
        LocalDate date = kst.toLocalDate();
        MidDay day = land.get().days().get(date);
        if (day == null) {
            return null;
        }
        boolean morning = kst.getHour() < NOON;
        String text = morning ? day.amText() : day.pmText();
        Integer pop = morning ? day.amPop() : day.pmPop();
        WeatherCondition condition = WeatherConditions.ofMidTerm(text);

        // 기온은 따로 받는 자료라 없을 수 있다. 없다고 날씨까지 내리지 않는다.
        DayTemperature temperature = store.midTemperature(area.midTaRegId(), now)
                .map(MidTemperature::days)
                .map(days -> days.get(date))
                .orElse(null);
        return Weather.daily(condition,
                temperature == null ? null : temperature.min(),
                temperature == null ? null : temperature.max(),
                pop,
                area.displayName(),
                land.get().announcedAt());
    }
}
