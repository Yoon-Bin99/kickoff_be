package com.kickoff.be.review.dto;

/**
 * 팀이 받은 리뷰 집계 (계약서 §7). 반올림을 타입 안으로 넣어, 어느 경로로 만들어도
 * 계약이 요구하는 값 — 소수 첫째 자리 반올림, 0건이면 null — 이 나오게 한다.
 */
public record ReviewStats(long count, Double average) {

    public static final ReviewStats EMPTY = new ReviewStats(0, null);

    public ReviewStats {
        average = (count == 0 || average == null) ? null : Math.round(average * 10) / 10.0;
    }
}
