package com.kickoff.be.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.oauth.service.SocialLoginService.SocialLoginResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 웹 OAuth 일회용 코드의 <b>만료</b> (계약서 §3-1, v1.26.0).
 *
 * 이 클래스가 지키는 성질은 둘이다 — <b>1회용</b>과 <b>60초</b>. 1회용은
 * {@code WebOAuthExchangeTest} 가 실제 흐름으로 보고, 만료는 여기서 본다.
 *
 * <b>시계를 갈아 끼우는 이유</b>: 실제로 기다리면 테스트 하나가 1분을 먹는다. 그렇다고
 * 검증하지 않고 두면, 코드가 URL 에 남는 설계에서 <b>만료가 실제로 도는지 아무도 모르는</b>
 * 상태가 된다. TTL 을 늘려 잡는 실수도 못 잡는다.
 */
class OAuthExchangeCodeStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");
    private static final SocialLoginResult RESULT =
            new SocialLoginResult("access", "refresh", true);

    @Test
    @DisplayName("발급 직후에는 교환된다")
    void freshCodeIsExchangeable() {
        OAuthExchangeCodeStore store = storeAt(NOW);

        assertThat(store.consume(store.issue(RESULT))).contains(RESULT);
    }

    @Test
    @DisplayName("59초 뒤에는 아직 유효하다 — 경계 안쪽")
    void validJustBeforeExpiry() {
        MutableClock clock = new MutableClock(NOW);
        OAuthExchangeCodeStore store = new OAuthExchangeCodeStore(clock);
        String code = store.issue(RESULT);

        clock.advance(Duration.ofSeconds(59));

        assertThat(store.consume(code)).contains(RESULT);
    }

    @Test
    @DisplayName("61초 뒤에는 만료다 — 주소창에 남은 코드가 쓸모없어진다")
    void expiresAfterTtl() {
        MutableClock clock = new MutableClock(NOW);
        OAuthExchangeCodeStore store = new OAuthExchangeCodeStore(clock);
        String code = store.issue(RESULT);

        clock.advance(Duration.ofSeconds(61));

        assertThat(store.consume(code)).isEmpty();
    }

    /** 교환되지 않은 코드가 영원히 쌓이면 안 된다 — 로그인을 시작만 하고 마치지 않는 경우다. */
    @Test
    @DisplayName("만료된 코드는 다음 발급 때 정리된다")
    void expiredCodesArePurgedOnNextIssue() {
        MutableClock clock = new MutableClock(NOW);
        OAuthExchangeCodeStore store = new OAuthExchangeCodeStore(clock);
        String abandoned = store.issue(RESULT);

        clock.advance(Duration.ofSeconds(61));
        store.issue(RESULT);

        assertThat(store.consume(abandoned)).isEmpty();
    }

    @Test
    @DisplayName("없는 코드·빈 값은 비어 있다")
    void unknownOrBlankIsEmpty() {
        OAuthExchangeCodeStore store = storeAt(NOW);

        assertThat(store.consume("없는-코드")).isEmpty();
        assertThat(store.consume("")).isEmpty();
        assertThat(store.consume(null)).isEmpty();
    }

    private OAuthExchangeCodeStore storeAt(Instant instant) {
        return new OAuthExchangeCodeStore(Clock.fixed(instant, ZoneOffset.UTC));
    }

    /** 앞으로만 가는 시계. {@code Clock.offset} 을 매번 새로 만들지 않으려고 둔다. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
