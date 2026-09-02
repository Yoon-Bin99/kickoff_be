package com.kickoff.be.email.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기본 어댑터 — 보내지 않고 로그로 남긴다 (계약서 §3-3).
 *
 * dev 에서 인증번호를 확인하는 유일한 통로다. 문자 쪽 {@code LoggingSmsClient} 와 달리
 * <b>EMAIL_ENABLED 가 켜졌는데 제공자가 없어도 예외를 던지지 않는다.</b> 발송이 비동기라
 * 던져 봐야 로그에만 남고, "설정이 빠졌다"는 사실은 이 경고 한 줄로 충분히 드러난다.
 *
 * SMTP 어댑터가 등록되면 @Primary 로 이걸 대체한다.
 */
@Slf4j
@Component
public class LoggingEmailClient implements EmailClient {

    private final EmailProperties properties;

    public LoggingEmailClient(EmailProperties properties) {
        this.properties = properties;
        // SmtpEmailClient 쪽 등록 로그와 짝이다. 둘 중 하나는 반드시 찍히므로,
        // 기동 로그만 봐도 어느 어댑터가 살아 있는지 알 수 있다.
        log.info("SMTP 미설정 — 메일은 로그로만 남깁니다 (EMAIL_ENABLED={})",
                properties.enabled());
    }

    @Override
    public void send(String to, String subject, String body) {
        if (properties.enabled()) {
            log.warn("메일 발송이 켜져 있는데 SMTP 어댑터가 없습니다 — 설정을 확인하세요. "
                    + "이번 건은 발송되지 않습니다: to={}, subject={}", to, subject);
            return;
        }
        // dev 전용. 본문에 코드가 들어 있어서 그대로 남긴다 — 이게 없으면 로컬에서
        // 재설정 흐름을 끝까지 밟을 방법이 없다.
        log.info("[메일 발송 생략] to={} subject={}\n{}", to, subject, body);
    }
}
