package com.kickoff.be.holiday.dto;

import java.util.List;

/**
 * GET /api/holidays?year=YYYY (계약서 §8-2).
 *
 * {@code source} 를 함께 내리는 이유는 {@link HolidaySource} 주석에 적었다 — 빈 목록과
 * "모른다"를 FE 가 구별해야 한다.
 */
public record HolidayResponse(int year, HolidaySource source, List<Holiday> holidays) {

    public static HolidayResponse none(int year) {
        return new HolidayResponse(year, HolidaySource.NONE, List.of());
    }

    public static HolidayResponse kakao(int year, List<Holiday> holidays) {
        return new HolidayResponse(year, HolidaySource.KAKAO, holidays);
    }
}
