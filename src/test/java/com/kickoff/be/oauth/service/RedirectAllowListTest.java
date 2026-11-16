package com.kickoff.be.oauth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.oauth.client.OAuthProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 복귀 주소 허용 목록의 패턴 규칙 (계약서 §3-1, open redirect 방지).
 *
 * <b>운영 주소를 정하기 전에 못 박아 둔다.</b> 앱(`kickoff://`)만 쓰던 목록에 웹 주소가
 * 들어오는데, {@code *} 를 어디에 두느냐로 안전과 구멍이 갈린다 — 아래
 * {@code suffixWildcardIsDangerous} 가 그 자리다.
 */
class RedirectAllowListTest {

    @Test
    @DisplayName("웹 주소 패턴을 그대로 받는다")
    void acceptsWebPatterns() {
        RedirectAllowList allowList = of("https://kickoff.example.com/*");

        assertThat(allowList.isAllowed("https://kickoff.example.com/auth/callback")).isTrue();
        assertThat(allowList.isAllowed("https://kickoff.example.com/login?next=/home"))
                .isTrue();
    }

    /**
     * {@code *} 는 {@code /} 도 삼킨다 — 경로 한 칸이 아니라 "그 뒤 전부"다. 웹은 복귀
     * 경로가 여럿이라 이 쪽이 맞다.
     */
    @Test
    @DisplayName("별표는 경로 구분자도 넘는다")
    void wildcardSpansSlashes() {
        RedirectAllowList allowList = of("https://kickoff.example.com/*");

        assertThat(allowList.isAllowed("https://kickoff.example.com/a/b/c")).isTrue();
    }

    /** 패턴은 <b>전체 일치</b>라서 다른 호스트가 끼어들 수 없다. */
    @Test
    @DisplayName("다른 호스트는 막힌다")
    void otherHostsAreRejected() {
        RedirectAllowList allowList = of("https://kickoff.example.com/*");

        assertThat(allowList.isAllowed("https://evil.example/steal")).isFalse();
        assertThat(allowList.isAllowed("http://kickoff.example.com/auth")).isFalse();
    }

    /**
     * <b>슬래시 없는 별표는 구멍이다.</b> {@code https://kickoff.example.com*} 는
     * {@code https://kickoff.example.com.evil.example} 에도 맞는다 — 공격자가 자기
     * 도메인 앞에 우리 호스트를 붙이면 토큰이 그쪽으로 간다.
     *
     * 막는 코드를 넣는 대신 테스트로 적어 둔다: 설정값은 배포할 때 사람이 넣는 값이고,
     * 무엇이 위험한지 아는 것이 먼저다(README 배포 절에도 적었다).
     */
    @Test
    @DisplayName("슬래시 없는 별표는 다른 도메인까지 연다 — 설정에 쓰면 안 된다")
    void suffixWildcardIsDangerous() {
        RedirectAllowList risky = of("https://kickoff.example.com*");

        assertThat(risky.isAllowed("https://kickoff.example.com.evil.example/steal")).isTrue();
    }

    /** 끝의 슬래시가 없으면 루트 복귀가 막힌다 — 필요하면 따로 넣는다. */
    @Test
    @DisplayName("루트로 돌아오려면 패턴을 따로 넣어야 한다")
    void rootNeedsItsOwnPattern() {
        assertThat(of("https://kickoff.example.com/*")
                .isAllowed("https://kickoff.example.com")).isFalse();
        assertThat(of("https://kickoff.example.com/*", "https://kickoff.example.com")
                .isAllowed("https://kickoff.example.com")).isTrue();
    }

    @Test
    @DisplayName("앱 스킴 패턴은 그대로 돈다")
    void appSchemesStillWork() {
        RedirectAllowList allowList = of("kickoff://*", "exp://*");

        assertThat(allowList.isAllowed("kickoff://auth")).isTrue();
        assertThat(allowList.isAllowed("exp://192.168.0.2:8081/--/auth")).isTrue();
        assertThat(allowList.isAllowed("myapp://auth")).isFalse();
    }

    private RedirectAllowList of(String... patterns) {
        return new RedirectAllowList(new OAuthProperties(null, null, List.of(patterns), null));
    }
}
