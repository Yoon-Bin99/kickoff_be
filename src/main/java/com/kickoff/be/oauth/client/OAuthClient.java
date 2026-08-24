package com.kickoff.be.oauth.client;

import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;

/**
 * 제공자와의 HTTP 왕복을 감추는 경계. 테스트는 이 인터페이스를 스텁으로 갈아끼워
 * 실제 카카오/네이버 키 없이 계정 결정 규칙을 전부 검증한다.
 */
public interface OAuthClient {

    AuthProvider provider();

    /**
     * 키가 설정돼 있는지. 키 없이도 서버는 떠야 하고, 키 없는 제공자는 등록되지 않아
     * UNSUPPORTED_PROVIDER 로 응답한다.
     */
    boolean isConfigured();

    /** 사용자를 보낼 제공자 로그인/동의 화면 URL. */
    String authorizeUrl(String state, String callbackUri);

    /**
     * code 를 토큰으로 교환하고 프로필까지 조회해 정규화한다.
     * callbackUri 는 authorize 때 쓴 값과 글자 그대로 같아야 제공자가 교환을 받아준다.
     */
    OAuthProfile fetchProfile(String code, String state, String callbackUri);
}
