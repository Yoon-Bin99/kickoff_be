package com.kickoff.be.passwordreset.service;

import com.kickoff.be.email.client.EmailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 재설정 코드 메일 발송 (계약서 §3-3, v1.23.0).
 *
 * AFTER_COMMIT 이라 롤백된 요청의 유령 메일이 나가지 않고, @Async 라 SMTP 왕복이 응답을
 * 붙잡지 않는다. <b>어떤 예외도 밖으로 내지 않는다</b> — 계약이 "발송 실패도 204"를
 * 요구하고, 실패가 응답에 비치면 그게 곧 존재 신호다.
 *
 * 여기서는 DB 를 쓰지 않는다. AFTER_COMMIT 안에서 저장하면 이미 끝난 트랜잭션에 얹혀
 * 조용히 사라지는데(v1.22.0 에서 겪었다), 발송만 하므로 그 함정에 닿지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailSender {

    private static final String SUBJECT = "[킥오프] 비밀번호 재설정 인증번호";

    private final EmailClient emailClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PasswordResetCodeIssuedEvent event) {
        try {
            emailClient.send(event.email(), SUBJECT, body(event.code()));
        } catch (RuntimeException e) {
            // 로그가 유일한 흔적이다. 사용자는 204 를 받았고, 코드가 안 오면 재요청한다.
            log.warn("비밀번호 재설정 메일 발송 실패 — to={}", event.email(), e);
        }
    }

    private String body(String code) {
        return """
                킥오프 비밀번호 재설정 인증번호입니다.

                인증번호: %s

                앱의 비밀번호 재설정 화면에 위 번호를 입력해 주세요.
                이 번호는 10분 동안 유효합니다.

                본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다.
                비밀번호는 그대로 유지됩니다.
                """.formatted(code);
    }
}
