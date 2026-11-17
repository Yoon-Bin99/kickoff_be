package com.kickoff.be.weather.client.dto;

/** 중기기온의 하루 — 최저·최고 (계약서 §8-3). 둘 중 하나만 온 날이 있어 nullable 이다. */
public record DayTemperature(Integer min, Integer max) {
}
