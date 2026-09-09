package com.kickoff.be.oauth.service;

import com.kickoff.be.oauth.service.SocialLoginService.SocialLoginResult;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 웹 복귀용 일회용 코드 (계약서 §3-1, v1.26.0).
 *
 * <b>왜 있는가.</b> 앱은 복귀 URL 이 {@code kickoff://} 딥링크라 토큰을 쿼리로 실어도
 * OS 가 앱에 넘기고 끝난다. 웹은 같은 URL 이 <b>주소창에 뜬다</b> — 브라우저 히스토리에
 * 남고, 그 페이지가 외부 자원을 하나라도 부르면 {@code Referer} 헤더로 URL 전체가
 * 제3자에게 간다. refresh 토큰은 30일짜리라 무게가 다르다.
 *
 * 그래서 웹에는 토큰 대신 이 코드를 주고, FE 가 POST 로 교환한다. 코드가 URL 에 남아도
 * 60초가 지났거나 이미 쓰였으면 아무 값이 없다.
 *
 * <b>인메모리다.</b> {@link OAuthStateStore} 와 같은 방식이다 — 서버를 내리면 진행 중이던
 * 로그인이 끊기고 사용자는 다시 로그인한다. 60초짜리 값이라 DB 에 둘 이유가 없고,
 * 마이그레이션 없이 넣을 수 있다. 여러 인스턴스로 늘리면 콜백을 받은 인스턴스와 교환을
 * 받는 인스턴스가 달라질 수 있어 공유 저장소가 필요해진다 — state 와 같은 한계다.
 */
@Component
public class OAuthExchangeCodeStore {

    /**
     * 60초. 콜백 302 를 받은 브라우저가 곧바로 교환을 부르는 흐름이라 길 이유가 없다.
     * 짧을수록 URL 에 남은 코드가 쓸모없어지는 시점이 빨라진다.
     */
    private static final Duration TTL = Duration.ofSeconds(60);

    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Issued> issued = new ConcurrentHashMap<>();
    private final Clock clock;

    public OAuthExchangeCodeStore() {
        this(Clock.systemUTC());
    }

    /**
     * 시계를 갈아 끼울 수 있게 열어 둔다. <b>만료를 테스트할 방법이 이것뿐이다</b> —
     * TTL 이 60초라 실제로 기다리면 테스트 하나가 1분을 먹는다. 만료는 이 클래스가
     * 지키려는 성질의 절반이라(나머지 절반이 1회용) 검증하지 않고 둘 수 없다.
     *
     * 스프링은 인자 없는 생성자를 쓴다 — 어느 것도 {@code @Autowired} 가 아니고 기본
     * 생성자가 있으면 그쪽을 고른다.
     */
    OAuthExchangeCodeStore(Clock clock) {
        this.clock = clock;
    }

    private record Issued(SocialLoginResult result, Instant expiresAt) {

        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }

    public String issue(SocialLoginResult result) {
        purgeExpired();
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        issued.put(code, new Issued(result, clock.instant().plus(TTL)));
        return code;
    }

    /**
     * <b>1회용이다</b> — 꺼내는 순간 없어져 같은 코드로 두 번 토큰을 받을 수 없다.
     *
     * 만료된 값도 {@code remove} 로 먼저 꺼낸다. 만료를 이유로 남겨 두면 그 항목이 다음
     * 정리까지 메모리에 머무는데, 어차피 쓸 수 없는 값이라 그 자리에서 버리는 게 낫다.
     */
    public Optional<SocialLoginResult> consume(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Issued found = issued.remove(code);
        if (found == null || found.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(found.result());
    }

    /** 교환되지 않은 코드가 쌓이지 않게 한다 — 로그인을 시작만 하고 마치지 않는 경우다. */
    private void purgeExpired() {
        Instant now = clock.instant();
        issued.values().removeIf(value -> value.isExpired(now));
    }
}
