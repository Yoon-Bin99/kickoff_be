package com.kickoff.be.support;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.dto.PushMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 실제 Expo 를 때리지 않고 무엇이 누구에게 나갔는지 기록한다.
 *
 * 실제 클라이언트는 예외를 밖으로 내지 않지만, 이 스텁은 {@link #willFail()} 로 일부러
 * 터뜨릴 수 있다 — 리스너가 예외를 삼켜 원 요청이 무사한지 검증하려는 것이다.
 */
public class StubPushClient implements PushClient {

    private final List<PushMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failNext;

    @Override
    public void send(List<PushMessage> messages) {
        if (failNext) {
            throw new IllegalStateException("스텁이 일부러 낸 발송 실패");
        }
        sent.addAll(messages);
    }

    public List<PushMessage> sent() {
        return List.copyOf(sent);
    }

    /** 마지막으로 나간 한 건. 없으면 null. */
    public PushMessage last() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    public void willFail() {
        this.failNext = true;
    }

    public void reset() {
        sent.clear();
        failNext = false;
    }
}
