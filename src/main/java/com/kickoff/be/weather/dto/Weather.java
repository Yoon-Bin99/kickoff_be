package com.kickoff.be.weather.dto;

import java.time.OffsetDateTime;

/**
 * 경기 시각의 예상 날씨 (계약서 §8-3, v1.36.0). {@code PostSummary}·{@code PostDetail}·
 * {@code RequestResponse} 의 {@code weather} 필드로 나간다. 없으면 필드가 null 이다.
 *
 * <b>예보가 언제 것인지를 함께 싣는다</b>({@code announcedAt}) — 예보는 바뀌는 값이라
 * 화면이 "언제 발표된 예보"인지 말할 수 있어야 하고, 출처 표시(공공누리 제1유형)도
 * 이 값으로 그린다.
 *
 * @param area 예보의 기준 지역. 시군구를 표에서 못 찾아 시/도 대표 지점을 썼으면 시/도만
 *             들어간다 — 화면이 "서울 기준"이라고 말할 수 있어야 한다 (v1.37.1).
 */
public record Weather(
        WeatherCondition condition,
        WeatherKind kind,
        Integer temperature,
        Integer temperatureMin,
        Integer temperatureMax,
        Integer precipitationProbability,
        String area,
        OffsetDateTime announcedAt
) {

    public static Weather hourly(WeatherCondition condition, Integer temperature,
                                 Integer pop, String area, OffsetDateTime announcedAt) {
        return new Weather(condition, WeatherKind.HOURLY, temperature, null, null,
                pop, area, announcedAt);
    }

    public static Weather daily(WeatherCondition condition, Integer min, Integer max,
                                Integer pop, String area, OffsetDateTime announcedAt) {
        return new Weather(condition, WeatherKind.DAILY, null, min, max,
                pop, area, announcedAt);
    }
}
