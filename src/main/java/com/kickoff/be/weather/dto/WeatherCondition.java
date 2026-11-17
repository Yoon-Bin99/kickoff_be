package com.kickoff.be.weather.dto;

/**
 * 날씨 (계약서 §8-3, v1.36.0). FE 가 이모지와 한글 이름으로 바꾼다.
 *
 * <b>여기에 판단을 담지 않는다</b> — "경기하기 좋음" 같은 값을 만들지 않는다. 예보는
 * 기상청만 할 수 있고(기상법 제17조), 킥오프는 받은 값을 옮길 뿐이다.
 *
 * {@code UNKNOWN} 은 "예보는 있는데 날씨 글자·숫자를 우리가 해석하지 못했다"는 뜻이다.
 * 예보가 아예 없으면 {@code Weather} 자체가 null 이지 UNKNOWN 이 아니다 — 기상청이
 * 코드를 늘렸을 때 기온까지 함께 사라지지 않게 하려고 둔 값이다.
 */
public enum WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    RAIN_SNOW,
    SNOW,
    SHOWER,
    UNKNOWN
}
