package com.kickoff.be.weather.dto;

/**
 * 어느 예보에서 나온 값인지 (계약서 §8-3, v1.36.0).
 *
 * 기온 칸이 달라서 FE 가 알아야 한다 — {@code HOURLY} 는 경기 시각의 기온 하나,
 * {@code DAILY} 는 그날의 최저·최고다.
 */
public enum WeatherKind {
    /** 단기예보 — 경기 시각의 값. */
    HOURLY,
    /** 중기예보 — 그날의 최저·최고 기온. */
    DAILY
}
