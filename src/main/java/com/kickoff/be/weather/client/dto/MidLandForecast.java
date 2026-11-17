package com.kickoff.be.weather.client.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/** 한 예보구역의 중기육상예보 보관분 — 날짜(KST)별 날씨 글자와 강수확률 (계약서 §8-3). */
public record MidLandForecast(OffsetDateTime announcedAt, Map<LocalDate, MidDay> days) {
}
