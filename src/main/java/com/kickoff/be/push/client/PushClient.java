package com.kickoff.be.push.client;

import com.kickoff.be.push.dto.PushMessage;
import java.util.List;

/**
 * 푸시 발송 경계. 소셜 로그인의 OAuthClient 와 같은 이유로 인터페이스를 둔다 —
 * 테스트가 실제 Expo 를 때리지 않고 무엇이 누구에게 나갔는지 검증할 수 있어야 한다.
 */
public interface PushClient {

    /** 실패하더라도 예외를 던지지 않는다. 푸시는 best-effort 다 (계약서 §8). */
    void send(List<PushMessage> messages);
}
