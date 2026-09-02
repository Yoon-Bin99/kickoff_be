package com.kickoff.be.email.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 어댑터 선택 (계약서 §3-3).
 *
 * <b>우선순위를 한 곳에 모은 이유가 있다.</b> 예전에는 어댑터마다 {@code @Primary} 를
 * 붙였는데, 그 방식으로 세 번째 어댑터를 더하면 <b>{@code @Primary} 빈이 둘이 되어 기동이
 * 죽는다</b> ({@code NoUniqueBeanDefinitionException}). 운영에는 SMTP 설정이 이미 들어가
 * 있어서, 거기에 Brevo 키를 넣는 순간 서비스 전체가 내려갔을 것이다 — 메일 하나 고치려다.
 *
 * 지금은 {@code @Primary} 가 이 메서드 하나뿐이라 그 충돌이 <b>구조적으로 불가능</b>하고,
 * 우선순위가 한 화면에 다 보인다.
 *
 * <pre>
 *   1. Brevo    BREVO_API_KEY 가 있으면       ← HTTP. Railway 가 SMTP 를 막아 이게 1순위다
 *   2. SMTP     host·username·password 셋 다  ← 로컬·다른 호스팅용으로 남겨 둔다
 *   3. Logging  아무 설정도 없으면             ← 보내지 않고 코드를 로그로. dev 의 정상 상태
 * </pre>
 *
 * <b>{@code EMAIL_ENABLED} 는 여기서 보지 않는다.</b> 스위치를 선택 조건에 넣으면 "키는
 * 있는데 꺼 둔" 상태와 "키가 없는" 상태가 한 모양이 되어, 왜 메일이 안 나가는지 구별할 수
 * 없다. 스위치는 각 어댑터가 본다.
 */
@Slf4j
@Configuration
public class EmailClientConfig {

    /**
     * 실제로 쓸 어댑터.
     *
     * 조건식이 "둘 중 하나라도 설정이 있을 때"인 이유: 아무 설정도 없는 dev 에서는 이
     * 빈을 아예 만들지 않아 {@link LoggingEmailClient}({@code @Component})가 그대로 남는다.
     */
    @Bean
    @Primary
    @ConditionalOnExpression("""
            !'${kickoff.email.brevo.api-key:}'.isEmpty()
            or (!'${spring.mail.host:}'.isEmpty()
                and !'${spring.mail.username:}'.isEmpty()
                and !'${spring.mail.password:}'.isEmpty())
            """)
    public EmailClient emailClient(
            JavaMailSender mailSender, EmailProperties properties,
            @Value("${kickoff.email.brevo.api-key:}") String brevoApiKey,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.port:587}") String port,
            @Value("${spring.mail.username:}") String username) {

        // 발신 주소는 두 어댑터가 같은 규칙을 쓴다 — EMAIL_FROM 이 있으면 그것,
        // 없으면 SMTP 계정. Brevo 는 <b>콘솔에서 인증한 주소</b>만 발신을 허용한다.
        String sender = properties.from() == null || properties.from().isBlank()
                ? username : properties.from();

        if (!brevoApiKey.isBlank()) {
            // <b>왜 그게 골라졌는지</b>까지 남긴다. SMTP 설정이 함께 있을 때 "왜 SMTP 로
            // 안 나가지"를 로그만 보고 알 수 있어야 한다. API 키는 찍지 않는다.
            log.info("메일 어댑터: Brevo (HTTP) — sender={}{} enabled={}", sender,
                    host.isBlank() ? "" : " · SMTP 설정도 있으나 Brevo 가 우선 ·",
                    properties.enabled());
            return new BrevoEmailClient(brevoApiKey, sender);
        }

        // env 를 바꿨을 때 반영됐는지 로그로 확인할 수 있어야 한다 — 이번 조사에서
        // "설정을 고쳤는데 그게 실제로 먹었나"를 못 보는 게 여러 번 걸렸다.
        log.info("메일 어댑터: SMTP — host={} port={} username={} enabled={}",
                host, port, username, properties.enabled());
        return new SmtpEmailClient(mailSender, properties, username);
    }
}
