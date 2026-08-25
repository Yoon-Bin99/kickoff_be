package com.kickoff.be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 실제 카카오 클라이언트 대신 스텁을 쓴다. 소셜·푸시와 달리 "키가 없으면 빠진다" 같은 장치가
 * 없어서(제공자가 하나뿐이라 레지스트리를 두지 않았다) @Primary 로 밀어낸다.
 */
@TestConfiguration
public class StubPlaceConfig {

    @Bean
    @Primary
    public StubPlaceSearchClient stubPlaceSearchClient() {
        return new StubPlaceSearchClient();
    }
}
