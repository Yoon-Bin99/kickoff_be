package com.kickoff.be.support;

import com.kickoff.be.oauth.entity.AuthProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 카카오만 "설정된" 상태로 만든다. 네이버는 일부러 두지 않는다 —
 * 키가 없는 제공자가 UNSUPPORTED_PROVIDER 로 나가는지도 같이 검증해야 하고,
 * 지금 실제 운영 상태(키 미발급)와도 같은 모양이다.
 */
@TestConfiguration
public class StubOAuthConfig {

    @Bean
    public StubOAuthClient kakaoStub() {
        return new StubOAuthClient(AuthProvider.KAKAO);
    }
}
