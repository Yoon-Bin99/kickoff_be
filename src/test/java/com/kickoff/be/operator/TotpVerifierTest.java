package com.kickoff.be.operator;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.operator.service.TotpVerifier;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TOTP 계산 자체 (RFC 6238 — 계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>직접 구현한 알고리즘이라 표준 시험 벡터로 못 박는다.</b> 우리 코드끼리만 맞춰 보면
 * 둘이 함께 틀려도 통과하고, 그러면 인증 앱이 보여 주는 코드가 영영 안 맞는다 — 운영자가
 * 로그인할 수 없는데 서버는 "틀린 코드"라고만 답하는 상태가 된다.
 *
 * 시험 벡터는 RFC 6238 부록 B 의 SHA-1 줄이다. 그 표는 8자리라 <b>뒤 6자리</b>를 본다
 * (잘라내기 뒤 나머지 연산만 다르고 계산은 같다).
 */
class TotpVerifierTest {

    /** RFC 6238 부록 B 의 공유 비밀 "12345678901234567890" 을 Base32 로 적은 것. */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpVerifier verifier = new TotpVerifier();

    @Test
    @DisplayName("RFC 6238 부록 B 의 SHA-1 시험 벡터와 같은 값을 낸다")
    void matchesRfcTestVectors() {
        // 표의 (시각, 8자리) 중 SHA-1 줄. 뒤 6자리만 본다.
        assertThat(verifier.currentCode(RFC_SECRET, Instant.ofEpochSecond(59L)))
                .isEqualTo("287082");
        assertThat(verifier.currentCode(RFC_SECRET, Instant.ofEpochSecond(1111111109L)))
                .isEqualTo("081804");
        assertThat(verifier.currentCode(RFC_SECRET, Instant.ofEpochSecond(1111111111L)))
                .isEqualTo("050471");
        assertThat(verifier.currentCode(RFC_SECRET, Instant.ofEpochSecond(1234567890L)))
                .isEqualTo("005924");
        assertThat(verifier.currentCode(RFC_SECRET, Instant.ofEpochSecond(2000000000L)))
                .isEqualTo("279037");
    }

    @Test
    @DisplayName("그 시각의 코드를 받아 준다")
    void acceptsCurrentCode() {
        Instant now = Instant.ofEpochSecond(1111111111L);

        assertThat(verifier.verify(RFC_SECRET, "050471", now)).isPresent();
    }

    /**
     * 기기 시계가 몇 초 어긋나는 일은 흔하다. 창을 앞뒤 한 칸으로 두면 최대 90초가
     * 유효한데, 틀리면 5회에 15분 잠기므로 공격자가 쓸 만한 창은 아니다.
     */
    @Test
    @DisplayName("앞뒤 한 칸(30초)까지 받아 준다")
    void acceptsOneStepDrift() {
        Instant base = Instant.ofEpochSecond(1111111111L);
        String previous = verifier.currentCode(RFC_SECRET, base.minusSeconds(30));
        String next = verifier.currentCode(RFC_SECRET, base.plusSeconds(30));

        assertThat(verifier.verify(RFC_SECRET, previous, base)).isPresent();
        assertThat(verifier.verify(RFC_SECRET, next, base)).isPresent();
    }

    @Test
    @DisplayName("두 칸 떨어진 코드는 받지 않는다")
    void rejectsTwoStepDrift() {
        Instant base = Instant.ofEpochSecond(1111111111L);
        String tooOld = verifier.currentCode(RFC_SECRET, base.minusSeconds(60));

        assertThat(verifier.verify(RFC_SECRET, tooOld, base)).isEmpty();
    }

    /**
     * <b>같은 코드를 또 받는지는 여기서 정하지 않는다.</b> 이 메서드는 "지금 유효한
     * 코드인가"만 답하고(같은 코드는 그 30초 동안 계속 유효하다), 재사용을 막는 것은
     * 세션 서비스의 일이다 — 어느 칸의 코드였는지를 돌려주는 이유가 그것이다.
     */
    @Test
    @DisplayName("같은 칸의 코드는 몇 번 물어도 같은 칸 번호를 돌려준다")
    void returnsTheStepForReuseChecks() {
        Instant base = Instant.ofEpochSecond(1111111111L);

        assertThat(verifier.verify(RFC_SECRET, "050471", base).getAsLong())
                .isEqualTo(verifier.verify(RFC_SECRET, "050471", base.plusSeconds(5))
                        .getAsLong());
    }

    @Test
    @DisplayName("모양이 틀린 입력은 조용히 거절한다 — 터지지 않는다")
    void rejectsMalformedInput() {
        Instant now = Instant.ofEpochSecond(1111111111L);

        assertThat(verifier.verify(RFC_SECRET, null, now)).isEmpty();
        assertThat(verifier.verify(RFC_SECRET, "12345", now)).isEmpty();
        assertThat(verifier.verify(RFC_SECRET, "abcdef", now)).isEmpty();
        assertThat(verifier.verify(null, "050471", now)).isEmpty();
        assertThat(verifier.verify("", "050471", now)).isEmpty();
    }

    /** 사람이 공백이나 하이픈을 넣어 적어 두는 일이 흔하다. */
    @Test
    @DisplayName("비밀키의 공백·하이픈은 무시한다")
    void ignoresSeparatorsInSecret() {
        Instant now = Instant.ofEpochSecond(1111111111L);
        String spaced = "GEZD GNBV GY3T QOJQ-GEZD-GNBV-GY3T-QOJQ";

        assertThat(verifier.currentCode(spaced, now)).isEqualTo("050471");
    }
}
