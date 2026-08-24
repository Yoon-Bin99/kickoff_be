package com.kickoff.be.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.review.dto.ReviewStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 반올림 경계는 통합 테스트로 다 훑기엔 조합이 많다. 규칙 자체는 여기서 못 박고,
 * 통합 테스트는 이 값이 실제 응답까지 흘러가는지만 본다.
 */
class ReviewStatsTest {

    @Test
    @DisplayName("소수 첫째 자리에서 반올림한다 — 내려가는 쪽, 올라가는 쪽 모두")
    void roundsToOneDecimal() {
        assertThat(new ReviewStats(3, 13 / 3.0).average()).isEqualTo(4.3);   // 4.333…
        assertThat(new ReviewStats(3, 14 / 3.0).average()).isEqualTo(4.7);   // 4.666…
        assertThat(new ReviewStats(2, 4.25).average()).isEqualTo(4.3);       // 경계는 올린다
        assertThat(new ReviewStats(1, 5.0).average()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("리뷰가 없으면 averageRating 은 null 이다 — 0.0 이 아니다")
    void noReviewsMeansNullAverage() {
        assertThat(new ReviewStats(0, null).average()).isNull();
        assertThat(ReviewStats.EMPTY.count()).isZero();
        assertThat(ReviewStats.EMPTY.average()).isNull();
    }
}
