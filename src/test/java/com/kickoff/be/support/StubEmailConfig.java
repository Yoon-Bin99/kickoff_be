package com.kickoff.be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubEmailConfig {

    /** @Primary 로 LoggingEmailClient 를 대체한다 — 테스트가 진짜 SMTP 를 부를 일이 없게. */
    @Bean
    @Primary
    public StubEmailClient emailClient() {
        return new StubEmailClient();
    }
}
