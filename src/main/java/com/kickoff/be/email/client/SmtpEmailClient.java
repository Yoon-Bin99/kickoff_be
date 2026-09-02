package com.kickoff.be.email.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * SMTP 어댑터 (계약서 §3-3, v1.23.0). Gmail SMTP 를 쓴다.
 *
 * <b>EMAIL_ENABLED 를 여기서 보지 않는다.</b> 서비스가 보는 게 아니라 이 클래스를
 * 아예 안 부르는 쪽으로 갈리는데, 그 판단은 {@link EmailClientConfig} 가 등록 조건으로
 * 한다. 문자 어댑터와 같은 이유다 — 스위치 검사를 어댑터 안에 두면, @Primary 로 대체될 때
 * 검사도 함께 사라져 dev 에서 진짜 메일이 나간다.
 *
 * 실패는 그대로 던진다. 호출자(비동기 리스너)가 받아 로그로만 남긴다 — 계약이 "발송
 * 실패도 204"를 요구하기 때문이고, 502 로 나가면 그 자체가 존재 신호가 된다.
 */
@Slf4j
public class SmtpEmailClient implements EmailClient {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final String from;

    public SmtpEmailClient(JavaMailSender mailSender, EmailProperties properties, String username) {
        this.mailSender = mailSender;
        this.properties = properties;
        // 발신 주소를 안 주면 로그인 계정을 쓴다. Gmail 은 이 둘이 다르면 거절한다.
        this.from = (properties.from() == null || properties.from().isBlank())
                ? username : properties.from();
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!properties.enabled()) {
            // 어댑터가 붙어 있어도 스위치가 꺼져 있으면 보내지 않는다. dev 에서 키를
            // 넣어 두고 스위치만 내려 두는 게 정상 상태다 — 문자와 같은 방침.
            log.info("[메일 발송 꺼짐] to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
