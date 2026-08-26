package com.kickoff.be.review.repository;

/**
 * 목록에서 팀마다 평점을 붙이려고 한 번에 긁어오는 집계 결과
 * (계약서 §2 TeamSummary, v1.10.0).
 *
 * PostRequestCount 와 같은 자리·같은 이유다 — 카드마다 집계 쿼리를 날리면 N+1 이다.
 */
public record TeamReviewStats(Long teamId, Long count, Double average) {
}
