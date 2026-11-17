package com.kickoff.be.weather.client.dto;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 한 격자의 단기예보 보관분 (계약서 §8-3).
 *
 * 칸의 키는 <b>KST 기준 예보 시각</b>이다. 기상청은 날짜와 시각을 따로 주고 시간대가
 * 적혀 있지 않다 — 받아 오는 쪽에서 한국 시각으로 못 박아 둔다.
 */
public record ShortTermForecast(OffsetDateTime announcedAt,
                                Map<LocalDateTime, HourSlot> slots) {
}
