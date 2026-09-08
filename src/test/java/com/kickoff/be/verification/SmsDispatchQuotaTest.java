package com.kickoff.be.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.verification.service.SmsDispatchQuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 인증 문자 발송의 <b>요청자 축</b> 한도.
 *
 * 기존 한도는 전화번호만 봤다. 발송은 인증이 필요 없는 경로라, 번호를 바꿔 가며 부르면
 * 아무것도 걸리지 않았다 — 번호 개수에 상한이 없기 때문이다. 그러면 문자 잔액이 소진되고
 * <b>우리 발신번호로 남에게 문자가 나간다.</b> 여기서 막으려는 것이 그것이다.
 *
 * 창(window)이 지나면 풀리는 동작은 검증하지 않는다. 10분·24시간을 실제로 기다리거나
 * 시계를 주입해야 하는데, 그 구조를 위해 클래스를 열면 정작 지키려는 성질(확인과 기록이
 * 한 번에 일어난다)이 흐려진다. 여기서는 <b>한도가 실제로 막는가</b>만 본다.
 */
class SmsDispatchQuotaTest {

    @Test
    @DisplayName("같은 IP 는 단기 한도를 넘으면 막힌다")
    void blocksWhenIpExceedsShortWindow() {
        SmsDispatchQuota quota = new SmsDispatchQuota();
        for (int i = 0; i < 10; i++) {
            assertThat(quota.tryAcquire("1.1.1.1")).isTrue();
        }

        assertThat(quota.tryAcquire("1.1.1.1"))
                .as("10분에 10회까지다")
                .isFalse();
    }

    /**
     * <b>이 축이 계약을 덮어써서는 안 된다.</b> 계약서 §3-2 는 한 번호에 1시간 5회를
     * 허용한다. IP 한도를 그보다 빡빡하게 잡으면 혼자 쓰는 사람이 계약이 허용한 횟수를
     * 못 채운다 — 문자가 안 와서 재요청을 누르는 정상 흐름이 막힌다.
     */
    @Test
    @DisplayName("계약이 번호에 허용한 1시간 5회는 IP 축에 막히지 않는다")
    void doesNotUndercutPerNumberAllowance() {
        SmsDispatchQuota quota = new SmsDispatchQuota();

        for (int i = 0; i < 5; i++) {
            assertThat(quota.tryAcquire("1.1.1.1"))
                    .as("%d 번째 발송", i + 1)
                    .isTrue();
        }
    }

    /**
     * 이게 없으면 한도가 아니라 <b>전면 차단</b>이다. 한 사람이 한도를 채우면 그 뒤로
     * 아무도 가입하지 못한다.
     */
    @Test
    @DisplayName("다른 IP 는 영향을 받지 않는다")
    void otherIpIsUnaffected() {
        SmsDispatchQuota quota = new SmsDispatchQuota();
        for (int i = 0; i < 10; i++) {
            quota.tryAcquire("1.1.1.1");
        }

        assertThat(quota.tryAcquire("2.2.2.2")).isTrue();
    }

    /**
     * 비용 방어선. IP 를 바꿔 가며 부르는 경우가 실제 공격 모양이라, IP 축만으로는
     * 막히지 않는다 — {@code X-Forwarded-For} 는 클라이언트가 지어낼 수 있는 값이다.
     * 전체 한도가 그 뒤를 받친다.
     */
    @Test
    @DisplayName("IP 를 바꿔도 전체 한도에서 막힌다")
    void blocksAtGlobalLimitEvenWithRotatingIps() {
        SmsDispatchQuota quota = new SmsDispatchQuota();
        int allowed = 0;
        for (int i = 0; i < 500; i++) {
            if (quota.tryAcquire("10.0.0." + i)) {
                allowed++;
            }
        }

        assertThat(allowed)
                .as("IP 가 매번 달라도 하루 전체 상한을 넘지 못한다")
                .isEqualTo(200);
    }

    /** IP 를 못 읽는 경우가 있다. 그때 무제한이 되면 그 경로가 곧 우회로가 된다. */
    @Test
    @DisplayName("IP 가 없어도 한도가 걸린다")
    void unknownIpIsStillLimited() {
        SmsDispatchQuota quota = new SmsDispatchQuota();

        for (int i = 0; i < 5; i++) {
            assertThat(quota.tryAcquire(null)).isTrue();
        }
        for (int i = 0; i < 5; i++) {
            assertThat(quota.tryAcquire("")).isTrue();
        }

        assertThat(quota.tryAcquire(null))
                .as("null 과 빈 문자열은 같은 키로 묶인다 — 둘을 나누면 그게 우회로가 된다")
                .isFalse();
    }
}
