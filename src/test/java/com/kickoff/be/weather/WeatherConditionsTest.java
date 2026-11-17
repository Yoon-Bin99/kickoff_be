package com.kickoff.be.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.weather.dto.WeatherCondition;
import com.kickoff.be.weather.service.WeatherConditions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기상청 값 -> 계약서 날씨 값 (계약서 §8-3). */
class WeatherConditionsTest {

    /** 비가 오는데 하늘 상태만 보고 "흐림"이라고 하면 우산을 두고 나간다. */
    @Test
    @DisplayName("단기 — 강수 형태가 하늘 상태보다 먼저다")
    void precipitationBeatsSky() {
        assertThat(WeatherConditions.ofShortTerm(1, 4)).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditions.ofShortTerm(2, 1)).isEqualTo(WeatherCondition.RAIN_SNOW);
        assertThat(WeatherConditions.ofShortTerm(3, 1)).isEqualTo(WeatherCondition.SNOW);
        assertThat(WeatherConditions.ofShortTerm(4, 1)).isEqualTo(WeatherCondition.SHOWER);
    }

    @Test
    @DisplayName("단기 — 강수 형태가 0 이면 하늘 상태로 정한다")
    void skyWhenNoPrecipitation() {
        assertThat(WeatherConditions.ofShortTerm(0, 1)).isEqualTo(WeatherCondition.CLEAR);
        assertThat(WeatherConditions.ofShortTerm(0, 3)).isEqualTo(WeatherCondition.PARTLY_CLOUDY);
        assertThat(WeatherConditions.ofShortTerm(0, 4)).isEqualTo(WeatherCondition.CLOUDY);
    }

    /** 기상청이 코드를 늘려도 기온까지 사라지면 안 된다 (계약서 §8-3). */
    @Test
    @DisplayName("단기 — 모르는 숫자는 UNKNOWN 이다")
    void unknownShortTermCodes() {
        assertThat(WeatherConditions.ofShortTerm(0, 2)).isEqualTo(WeatherCondition.UNKNOWN);
        assertThat(WeatherConditions.ofShortTerm(9, 1)).isEqualTo(WeatherCondition.UNKNOWN);
        assertThat(WeatherConditions.ofShortTerm(null, null)).isEqualTo(WeatherCondition.UNKNOWN);
    }

    /** "구름많고 비"를 구름으로 떨어뜨리지 않는 것이 이 순서의 이유다. */
    @Test
    @DisplayName("중기 — 비가 섞인 글자는 구름보다 먼저 맞는다")
    void midTermOrder() {
        assertThat(WeatherConditions.ofMidTerm("구름많고 비")).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditions.ofMidTerm("흐리고 눈")).isEqualTo(WeatherCondition.SNOW);
        assertThat(WeatherConditions.ofMidTerm("구름많고 소나기"))
                .isEqualTo(WeatherCondition.SHOWER);
        assertThat(WeatherConditions.ofMidTerm("흐리고 비/눈"))
                .isEqualTo(WeatherCondition.RAIN_SNOW);
        assertThat(WeatherConditions.ofMidTerm("구름많고 비 또는 눈"))
                .isEqualTo(WeatherCondition.RAIN_SNOW);
    }

    @Test
    @DisplayName("중기 — 맑음·구름많음·흐림")
    void midTermPlainWords() {
        assertThat(WeatherConditions.ofMidTerm("맑음")).isEqualTo(WeatherCondition.CLEAR);
        assertThat(WeatherConditions.ofMidTerm("구름많음"))
                .isEqualTo(WeatherCondition.PARTLY_CLOUDY);
        assertThat(WeatherConditions.ofMidTerm("흐림")).isEqualTo(WeatherCondition.CLOUDY);
    }

    /**
     * 활용가이드가 적어 둔 <b>하늘상태 11가지 전부</b>다 (중기예보 조회서비스
     * 오픈API활용가이드 2024-11판, "하늘상태" 절). UNKNOWN 이 하나도 없어야 한다 —
     * 하나라도 UNKNOWN 이면 그 날씨의 이모지가 화면에서 사라진다.
     */
    @Test
    @DisplayName("중기 — 가이드에 적힌 11가지를 모두 알아본다")
    void everyDocumentedMidTermWord() {
        assertThat(WeatherConditions.ofMidTerm("맑음")).isEqualTo(WeatherCondition.CLEAR);
        assertThat(WeatherConditions.ofMidTerm("구름많음"))
                .isEqualTo(WeatherCondition.PARTLY_CLOUDY);
        assertThat(WeatherConditions.ofMidTerm("구름많고 비")).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditions.ofMidTerm("구름많고 눈")).isEqualTo(WeatherCondition.SNOW);
        assertThat(WeatherConditions.ofMidTerm("구름많고 비/눈"))
                .isEqualTo(WeatherCondition.RAIN_SNOW);
        assertThat(WeatherConditions.ofMidTerm("구름많고 소나기"))
                .isEqualTo(WeatherCondition.SHOWER);
        assertThat(WeatherConditions.ofMidTerm("흐림")).isEqualTo(WeatherCondition.CLOUDY);
        assertThat(WeatherConditions.ofMidTerm("흐리고 비")).isEqualTo(WeatherCondition.RAIN);
        assertThat(WeatherConditions.ofMidTerm("흐리고 눈")).isEqualTo(WeatherCondition.SNOW);
        assertThat(WeatherConditions.ofMidTerm("흐리고 비/눈"))
                .isEqualTo(WeatherCondition.RAIN_SNOW);
        assertThat(WeatherConditions.ofMidTerm("흐리고 소나기"))
                .isEqualTo(WeatherCondition.SHOWER);
    }

    @Test
    @DisplayName("중기 — 모르는 글자와 빈 값은 UNKNOWN 이다")
    void midTermUnknown() {
        assertThat(WeatherConditions.ofMidTerm("안개")).isEqualTo(WeatherCondition.UNKNOWN);
        assertThat(WeatherConditions.ofMidTerm("")).isEqualTo(WeatherCondition.UNKNOWN);
        assertThat(WeatherConditions.ofMidTerm(null)).isEqualTo(WeatherCondition.UNKNOWN);
    }
}
