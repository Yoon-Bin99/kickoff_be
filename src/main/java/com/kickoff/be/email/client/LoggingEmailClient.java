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
        // <b>이 줄은 언제나 찍힌다.</b> 이 클래스는 @Component 라 SMTP 설정 여부와
        // 무관하게 항상 만들어지고, SmtpEmailClient 가 @Primary 로 그 위에 얹힌다.
        //
        // 처음에는 "둘 중 하나만 찍힌다"고 적었다가 실제로 오해를 만들었다 — 운영에서
        // 두 줄이 다 보이자 "빈이 두 개라 잘못 주입된 것 아니냐"는 조사가 붙었다.
        // 그래서 문구에 "준비"와 "대체됨"을 넣어, 이 줄만으로는 아직 아무것도 정해지지
        // 않았음을 드러낸다.
        log.info("메일 로그 어댑터 준비 (SMTP 가 등록되면 대체됨, EMAIL_ENABLED={})",
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
