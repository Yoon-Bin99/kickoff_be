package com.kickoff.be.support;

import com.kickoff.be.chatlink.client.ChatLinkProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 장소 검색 스텁과 같은 이유로 @Primary 다 — 제공자가 하나뿐이라 레지스트리가 없다. */
@TestConfiguration
public class StubReportContextConfig {

    @Bean
    @Primary
    public StubReportContextClient stubReportContextClient(ChatLinkProperties properties) {
        return new StubReportContextClient(properties);
    }
}
