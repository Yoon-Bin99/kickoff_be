package com.kickoff.be.oauth.service;

import com.kickoff.be.oauth.entity.AuthProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * CSRF 방지용 state 와, 콜백에서 필요한 값을 함께 보관한다.
 *
 * 콜백 요청은 제공자가 보내는 것이라 <b>Host 헤더를 믿을 수 없고</b> 원래 어디로 돌아가야
 * 하는지도 모른다. 그래서 authorize 시점에 정해진 복귀 URL 과 콜백 URI 를 state 에 묶어 둔다.
 * 콜백은 state 로만 이 값들을 되찾는다 — 요청에서 다시 뽑지 않는다.
 *
 * 인메모리라 서버를 내리면 진행 중이던 로그인은 끊긴다. 단일 인스턴스 개발 환경 기준이고,
 * 여러 인스턴스로 늘리면 공유 저장소로 옮겨야 한다.
 */
@Component
public class OAuthStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

    /**
     * @param redirect    로그인 완료 후 앱으로 돌아갈 URL (허용 목록 통과본)
     * @param callbackUri 제공자에 넘긴 콜백 URI. 토큰 교환 때 글자 그대로 다시 보내야 한다
     */
    public record PendingLogin(AuthProvider provider, String redirect, String callbackUri,
                               Instant expiresAt) {

        boolean isExpired(Instant now) {
            return expiresAt.isBefore(now);
        }
    }

    public String issue(AuthProvider provider, String redirect, String callbackUri) {
        purgeExpired();
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        pending.put(state,
                new PendingLogin(provider, redirect, callbackUri, Instant.now().plus(TTL)));
        return state;
    }

    /** 일회용이다 — 한 번 쓰면 없어져 같은 state 로 두 번 로그인할 수 없다. */
    public Optional<PendingLogin> consume(String state) {
        if (state == null || state.isBlank()) {
            return Optional.empty();
        }
        PendingLogin found = pending.remove(state);
        if (found == null || found.isExpired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(found);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        pending.values().removeIf(login -> login.isExpired(now));
    }
}
