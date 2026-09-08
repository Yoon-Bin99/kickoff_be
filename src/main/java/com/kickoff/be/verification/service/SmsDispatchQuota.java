package com.kickoff.be.verification.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 인증 문자 발송을 <b>요청자 기준</b>으로 제한한다 (계약서 §3-2 의 한도와 별개 축).
 *
 * <b>왜 필요한가.</b> 기존 한도는 전화번호 하나만 본다 — 같은 번호로 1분 1회, 1시간 5회다.
 * 그런데 발송은 인증이 필요 없는 엔드포인트라, 번호를 바꿔 가며 부르면 아무 제한도 걸리지
 * 않는다. 번호 개수에는 상한이 없기 때문이다. 그러면 두 가지가 동시에 일어난다:
 * 건당 과금되는 문자 잔액이 소진되고, <b>우리 발신번호로 남에게 원치 않는 문자가 나간다.</b>
 * 두 번째가 더 무겁다 — 받는 사람 입장에서는 우리가 보낸 것이다.
 *
 * 그래서 축을 두 개 더 둔다. IP 당 한도와 전체 한도다. 번호당 한도는 그대로 남고, 셋을
 * 모두 통과해야 발송된다.
 *
 * <b>인메모리다.</b> 재기동하면 카운터가 풀린다. 마이그레이션 없이 넣을 수 있고, 목적이
 * "무제한 발송을 막는 것"이지 "정확한 회계"가 아니라서 그 정도면 충분하다 — 공격자가
 * 한도를 풀려고 우리 서버를 재기동시킬 수는 없다. 여러 인스턴스로 늘리면 인스턴스마다
 * 따로 세므로 실질 한도가 인스턴스 수만큼 커진다. 그때는 공유 저장소로 옮겨야 한다
 * ({@code OAuthStateStore} 와 같은 한계다).
 *
 * 값을 넉넉하게 잡은 이유가 있다. 지인 테스트처럼 <b>한 Wi-Fi 에서 여러 명이 가입하는</b>
 * 상황이 정상 경로에 있다. 공인 IP 가 같아 한 사람으로 보이므로, 빡빡하게 잡으면 실제
 * 사용자가 막힌다. 막으려는 것은 자동화된 대량 발송이지 같이 앉아 가입하는 사람들이 아니다.
 */
@Component
public class SmsDispatchQuota {

    /**
     * IP 당 단기 한도.
     *
     * <b>번호당 한도보다 느슨해야 한다.</b> 계약서 §3-2 는 한 번호에 1시간 5회를 허용하는데,
     * IP 한도를 그보다 빡빡하게 잡으면 <b>혼자 쓰는 사람도 계약이 허용한 횟수를 못 채운다</b> —
     * 문자가 안 와서 재요청을 누르는 정상 흐름이 막힌다. 처음 3회로 잡았다가
     * PhoneVerificationTest 의 "1시간에 5회를 넘으면 429" 가 4번째에서 깨져 그 모순이 드러났다.
     * 한 IP 에 여러 명이 앉아 있을 수 있으니 그보다 넉넉히 둔다.
     */
    private static final Duration IP_WINDOW_SHORT = Duration.ofMinutes(10);
    private static final int IP_LIMIT_SHORT = 10;

    /** IP 당 하루 한도. 한 Wi-Fi 에 모인 지인 테스트를 넘지 않을 만큼. */
    private static final Duration IP_WINDOW_LONG = Duration.ofHours(24);
    private static final int IP_LIMIT_LONG = 30;

    /** 비용 방어선. 모든 IP 를 합쳐 하루 이만큼을 넘기지 않는다. */
    private static final Duration GLOBAL_WINDOW = Duration.ofHours(24);
    private static final int GLOBAL_LIMIT = 200;

    /** IP 별 발송 시각. 창이 지난 값은 조회할 때 정리한다. */
    private final Map<String, Deque<Instant>> perIp = new ConcurrentHashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    /**
     * 발송해도 되는지 본다. 되면 사용량을 기록하고 {@code true}.
     *
     * <b>확인과 기록을 한 메서드에서 한다.</b> 나누면 그 사이에 다른 요청이 끼어 둘 다
     * 통과하는 경합이 생긴다 — 한도가 있는데 넘는 상태다.
     */
    public synchronized boolean tryAcquire(String clientIp) {
        Instant now = Instant.now();
        purge(global, now.minus(GLOBAL_WINDOW));
        if (global.size() >= GLOBAL_LIMIT) {
            return false;
        }

        String key = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        Deque<Instant> mine = perIp.computeIfAbsent(key, k -> new ArrayDeque<>());
        purge(mine, now.minus(IP_WINDOW_LONG));
        if (mine.size() >= IP_LIMIT_LONG || countSince(mine, now.minus(IP_WINDOW_SHORT))
                >= IP_LIMIT_SHORT) {
            return false;
        }

        mine.addLast(now);
        global.addLast(now);
        // IP 가 계속 바뀌는 공격이면 맵이 자란다. 창이 빈 항목은 여기서 걷어낸다 —
        // 이걸 안 하면 항목마다 빈 큐가 영원히 남는다.
        perIp.values().removeIf(Deque::isEmpty);
        return true;
    }

    /**
     * 쌓인 사용량을 지운다. <b>테스트가 쓴다.</b>
     *
     * 이 빈은 싱글턴이라 한 컨텍스트 안의 테스트들이 카운터를 공유한다. 게다가 MockMvc
     * 요청은 전부 같은 주소(127.0.0.1)에서 온 것으로 보여 한 IP 로 묶인다 — 그래서 문자를
     * 세 번 보내는 순간 그 뒤의 모든 테스트가 429 를 받는다. 실제로 이 메서드를 넣기 전에
     * ExistingAccountTest 세 건이 그렇게 깨졌다.
     *
     * 테스트가 매번 테이블을 비우는 것과 같은 성격이다. 상태가 요청 사이에 남는 것이
     * 이 클래스의 요점이므로, 테스트 쪽에서 그 상태를 지울 방법이 있어야 한다.
     */
    public synchronized void reset() {
        perIp.clear();
        global.clear();
    }

    private void purge(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
            times.removeFirst();
        }
    }

    private long countSince(Deque<Instant> times, Instant cutoff) {
        return times.stream().filter(at -> !at.isBefore(cutoff)).count();
    }
}
