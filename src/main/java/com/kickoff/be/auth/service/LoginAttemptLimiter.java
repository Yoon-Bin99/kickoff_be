package com.kickoff.be.auth.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 로그인 실패가 반복되면 잠시 막는다 (계약서 §0 {@code LOGIN_RATE_LIMITED}, v1.25.1).
 *
 * <b>왜 필요한가.</b> 문자·메일·AI·재설정에는 전부 한도가 있는데 로그인만 없었다. 그래서
 * 비밀번호를 무제한으로 찍어 볼 수 있었다 — 이메일 주소는 {@code availability} 로 존재
 * 여부까지 확인되므로, 공격자는 "있는 계정"만 골라 시도할 수 있다.
 *
 * <b>이메일 기준이다.</b> IP 기준이 아닌 이유가 있다 — 지키려는 것은 <b>한 계정</b>이고,
 * 공격자는 IP 를 바꿀 수 있어도 노리는 계정은 바꿀 수 없다. 대신 한 IP 에서 여러 계정을
 * 훑는 경우는 이 한도로 못 막는다(계정마다 카운터가 따로다). 그건 다른 축의 문제이고,
 * 여기서는 계약이 정한 것만 한다.
 *
 * <b>성공하면 즉시 푼다.</b> 안 그러면 비밀번호를 몇 번 틀린 사용자가 맞는 비밀번호를 대고도
 * 15분을 기다려야 한다 — 공격을 막으려다 본인을 막는 셈이다.
 *
 * <b>인메모리다.</b> 재기동하면 카운터가 풀린다. 마이그레이션 없이 넣을 수 있고, 목적이
 * "무제한 시도를 막는 것"이라 그 정도면 충분하다 — 공격자가 한도를 풀려고 우리 서버를
 * 재기동시킬 수는 없다. 여러 인스턴스로 늘리면 인스턴스마다 따로 세므로 실질 한도가
 * 인스턴스 수만큼 커진다. 그때는 공유 저장소로 옮겨야 한다.
 */
@Component
public class LoginAttemptLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_FAILURES = 10;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    /** 지금 시도해도 되는가. 창 안의 실패가 한도 미만이면 통과다. */
    public synchronized boolean isAllowed(String email) {
        Deque<Instant> mine = failures.get(keyOf(email));
        if (mine == null) {
            return true;
        }
        purge(mine);
        return mine.size() < MAX_FAILURES;
    }

    public synchronized void recordFailure(String email) {
        Deque<Instant> mine = failures.computeIfAbsent(keyOf(email), k -> new ArrayDeque<>());
        purge(mine);
        mine.addLast(Instant.now());
    }

    /** 성공하면 그 계정의 기록을 지운다. */
    public synchronized void reset(String email) {
        failures.remove(keyOf(email));
    }

    /** 테스트가 쌓인 상태를 지운다 — 싱글턴이라 테스트끼리 카운터를 공유한다. */
    public synchronized void resetAll() {
        failures.clear();
    }

    /**
     * 대소문자를 무시해 한 키로 묶는다. 로그인 자체가 대소문자를 무시해 계정을 찾으므로
     * ({@code AuthService.findForLogin}), 키를 그대로 쓰면 {@code Kim@…} 과 {@code kim@…} 이
     * 다른 카운터가 되어 한도가 두 배가 된다 — 같은 계정을 노리는 시도인데 나뉜다.
     */
    private String keyOf(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void purge(Deque<Instant> times) {
        Instant cutoff = Instant.now().minus(WINDOW);
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.removeFirst();
        }
    }
}
