package com.kickoff.be.team.dto;

/**
 * 전적 요약 (계약서 §4-1, v1.8.0). 기록 0건이면 전부 0 이다.
 *
 * 컴포넌트가 {@code Long} 인 이유는 집계 쿼리가 내놓는 타입 그대로이기 때문이다.
 * 기록이 하나도 없는 팀은 {@code sum(...)} 이 null 을 주므로, 그 null 을 아래 compact
 * 생성자가 0 으로 바꾼다 — 계약서가 약속한 값은 null 이 아니라 0 이다.
 *
 * <b>생성자를 하나로 유지할 것.</b> 예전에는 여기에 {@code (long, long, long)} 정규 생성자와
 * {@code (Long, Long, Long)} 편의 생성자가 함께 있었는데, JPQL 의 {@code select new ...} 가
 * 둘 중 어느 것을 쓸지는 Hibernate 가 고르고 그 선택이 <b>JVM 실행마다 달라질 수 있다</b>
 * ({@code getDeclaredConstructors()} 의 순서는 규정돼 있지 않다). 원시형 쪽이 뽑힌 실행에서는
 * 기록 0건 팀의 null 이 그대로 넘어가 IllegalArgumentException 이 나고, 팀 페이지와 글
 * 상세가 통째로 500 이 됐다 — 코드를 바꾸지 않아도 재시작만으로 뒤집히는 종류의 고장이다.
 * 실제로 같은 커밋에서 전체 테스트가 한 번은 53개 실패, 다음 실행에서는 전부 통과했다.
 *
 * 그래서 후보를 하나만 남긴다. 생성자가 다시 늘어나면 RecordSummaryTest 가 깨진다.
 */
public record RecordSummary(Long wins, Long draws, Long losses) {

    public static final RecordSummary EMPTY = new RecordSummary(0L, 0L, 0L);

    public RecordSummary {
        wins = orZero(wins);
        draws = orZero(draws);
        losses = orZero(losses);
    }

    private static Long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
