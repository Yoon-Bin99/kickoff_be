package com.kickoff.be.weather.client.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/** 한 기온 지점의 중기기온 보관분 (계약서 §8-3). */
public record MidTemperature(OffsetDateTime announcedAt, Map<LocalDate, DayTemperature> days) {
}
