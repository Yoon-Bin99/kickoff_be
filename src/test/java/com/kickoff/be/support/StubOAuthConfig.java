package com.kickoff.be.support;

import com.kickoff.be.oauth.entity.AuthProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 카카오·네이버 둘 다 "설정된" 상태로 만든다. v1.3.2 에서 이메일 없을 때의 처리가
 * 제공자마다 갈려서(카카오는 가입 허용, 네이버는 거절) 양쪽이 다 필요하다.
 * 키 없는 제공자가 UNSUPPORTED_PROVIDER 로 나가는지는 GOOGLE 로 검증한다.
 */
@TestConfiguration
public class StubOAuthConfig {

    @Bean
    public StubOAuthClient kakaoStub() {
        return new StubOAuthClient(AuthProvider.KAKAO);
    }

    @Bean
    public StubOAuthClient naverStub() {
        return new StubOAuthClient(AuthProvider.NAVER);
    }
}
