package com.kickoff.be.weather.service;

import com.kickoff.be.weather.dto.WeatherCondition;

/**
 * 기상청의 값을 계약서의 날씨 값으로 옮긴다 (계약서 §8-3).
 *
 * <b>옮기기만 한다.</b> 여기서 판단을 섞지 않는다 — "비가 올 수도 있음" 같은 중간 값을
 * 만들면 그건 기상청이 하지 않은 예보가 된다(기상법 제17조).
 *
 * 표에 없는 값은 {@code UNKNOWN} 이다. 기상청이 코드를 늘렸을 때 기온까지 함께 사라지지
 * 않게 하려는 것이다 — 모르는 하늘 상태 하나 때문에 화면이 비면 손해가 더 크다.
 */
public final class WeatherConditions {

    private WeatherConditions() {
    }

    /**
     * 단기예보 — 강수 형태(PTY)가 먼저다 (계약서 §8-3). 비가 오는데 하늘 상태만 보고
     * "흐림"이라고 하면 우산을 두고 나간다.
     */
    public static WeatherCondition ofShortTerm(Integer pty, Integer sky) {
        if (pty != null && pty != 0) {
            return switch (pty) {
                case 1 -> WeatherCondition.RAIN;
                case 2 -> WeatherCondition.RAIN_SNOW;
                case 3 -> WeatherCondition.SNOW;
                case 4 -> WeatherCondition.SHOWER;
                default -> WeatherCondition.UNKNOWN;
            };
        }
        if (sky == null) {
            return WeatherCondition.UNKNOWN;
        }
        return switch (sky) {
            case 1 -> WeatherCondition.CLEAR;
            case 3 -> WeatherCondition.PARTLY_CLOUDY;
            case 4 -> WeatherCondition.CLOUDY;
            default -> WeatherCondition.UNKNOWN;
        };
    }

    /**
     * 중기예보 — 글자로 온다 ("구름많고 비", "맑음", "흐리고 눈" 등).
     *
     * <b>순서가 규칙이다</b> (계약서 §8-3): 소나기 → 비/눈 → 눈 → 비 → 맑음 → 구름많음 →
     * 흐림. "구름많고 비"처럼 두 낱말이 함께 오는 글자가 있어서, 위에서부터 먼저 맞는
     * 것을 쓴다 — 비가 섞인 날을 "구름많음"으로 떨어뜨리지 않으려는 순서다.
     */
    public static WeatherCondition ofMidTerm(String text) {
        if (text == null || text.isBlank()) {
            return WeatherCondition.UNKNOWN;
        }
        String value = text.strip();
        if (value.contains("소나기")) {
            return WeatherCondition.SHOWER;
        }
        if (value.contains("비/눈") || value.contains("비 또는 눈") || value.contains("눈/비")
                || value.contains("눈 또는 비")) {
            return WeatherCondition.RAIN_SNOW;
        }
        if (value.contains("눈")) {
            return WeatherCondition.SNOW;
        }
        if (value.contains("비")) {
            return WeatherCondition.RAIN;
        }
        if (value.contains("맑음")) {
            return WeatherCondition.CLEAR;
        }
        if (value.contains("구름많음")) {
            return WeatherCondition.PARTLY_CLOUDY;
        }
        if (value.contains("흐림")) {
            return WeatherCondition.CLOUDY;
        }
        return WeatherCondition.UNKNOWN;
    }
}
