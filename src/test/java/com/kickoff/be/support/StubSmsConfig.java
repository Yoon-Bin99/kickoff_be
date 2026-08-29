package com.kickoff.be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 문자 발송을 테스트에서 관측 가능하게 만든다 (계약서 §3-2). */
@TestConfiguration
public class StubSmsConfig {

    @Bean
    @Primary
    public StubSmsClient stubSmsClient() {
        return new StubSmsClient();
    }
}
