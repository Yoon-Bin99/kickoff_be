package com.kickoff.be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubAiSupportConfig {

    /**
     * 실제 어댑터는 키가 있을 때만 등록되므로 테스트에는 원래 없다. @Primary 는 혹시
     * 키가 들어온 환경에서 테스트가 <b>진짜 제공자를 부르는 일</b>을 막는다.
     */
    @Bean
    @Primary
    public StubAiSupportClient aiSupportClient() {
        return new StubAiSupportClient();
    }
}
