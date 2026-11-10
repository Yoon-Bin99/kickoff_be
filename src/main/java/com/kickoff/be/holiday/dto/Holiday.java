package com.kickoff.be.holiday.dto;

import java.time.LocalDate;

/**
 * 공휴일 하루 (계약서 §8-2).
 *
 * @param date 날짜. {@code LocalDate} 라 {@code "2026-01-01"} 로 직렬화된다 (§4-1 의
 *             {@code playedOn} 과 같은 형식).
 * @param name 카카오가 준 제목 원문(예: "신정", "설날", "임시공휴일"). <b>우리가 고치지
 *             않는다</b> — 임시공휴일처럼 이름이 그때그때 정해지는 것이 있어서, 손대면
 *             원문과 어긋난다. 계약서가 정한 50자 상한만 지킨다(넘는 제목은 실제로 없다)
 */
public record Holiday(LocalDate date, String name) {
}
