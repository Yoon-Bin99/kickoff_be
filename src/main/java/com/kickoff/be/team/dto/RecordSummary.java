package com.kickoff.be.team.dto;

/**
 * 전적 요약 (계약서 §4-1, v1.8.0). 기록 0건이면 전부 0 이다.
 *
 * 집계 쿼리는 기록이 없을 때 sum 이 null 인 행 하나를 준다. 그대로 내보내면 계약서가
 * 약속한 0 이 아니라 null 이 나가므로 여기서 막는다.
 */
public record RecordSummary(long wins, long draws, long losses) {

    public static final RecordSummary EMPTY = new RecordSummary(0, 0, 0);

    /** JPQL 의 {@code new ...} 가 쓰는 생성자. sum 은 Long 이고 비어 있으면 null 이다. */
    public RecordSummary(Long wins, Long draws, Long losses) {
        this(orZero(wins), orZero(draws), orZero(losses));
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
