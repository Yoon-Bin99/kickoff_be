package com.kickoff.be.email.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 어댑터 선택 (계약서 §3-3, v1.23.0).
 *
 * <b>SMTP 계정이 다 차 있을 때만 등록된다.</b> 빈 값으로라도 등록하면 매 발송이 인증
 * 실패로 끝나는데, 발송이 비동기라 그 실패는 로그에만 남는다 — 사용자는 "메일이 안 온다"만
 * 보고, 우리는 "제공자가 이상하다"로 읽는다. 설정이 빠진 것과 구별되지 않는다.
 *
 * 등록되지 않으면 {@link LoggingEmailClient} 가 남아 코드를 로그로만 남긴다. dev 의
 * 정상 상태가 그쪽이다.
 *
 * <b>{@code EMAIL_ENABLED} 는 등록 조건에 넣지 않는다.</b> 넣으면 "키는 있는데 꺼 둔"
 * 상태와 "키가 없는" 상태가 한 모양이 되어, 왜 메일이 안 나가는지 구별할 수 없다.
 * 스위치는 어댑터가 본다.
 */
@Configuration
public class EmailClientConfig {

    @Bean
    @Primary
    @ConditionalOnExpression("""
            !'${spring.mail.host:}'.isEmpty()
            and !'${spring.mail.username:}'.isEmpty()
            and !'${spring.mail.password:}'.isEmpty()
            """)
    public EmailClient smtpEmailClient(JavaMailSender mailSender, EmailProperties properties,
                                       @Value("${spring.mail.username:}") String username) {
        return new SmtpEmailClient(mailSender, properties, username);
    }
}
