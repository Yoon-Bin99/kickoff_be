package com.kickoff.be.support;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.client.OAuthClient;
import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;

/**
 * 제공자 HTTP 왕복을 대신하는 스텁. 실제 카카오/네이버 키 없이 계정 결정 규칙을 검증하려고 둔다.
 *
 * 이 스텁만 {@code isConfigured() == true} 라서, 키가 비어 있는 실제 클라이언트는
 * OAuthClientRegistry 에서 자동으로 빠진다. 별도 @Primary 나 목 설정이 필요 없다.
 */
public class StubOAuthClient implements OAuthClient {

    private final AuthProvider provider;

    private OAuthProfile nextProfile = new OAuthProfile("stub-1", "스텁");
    private boolean failNext;
    private String lastState;
    private String lastCallbackUri;

    public StubOAuthClient(AuthProvider provider) {
        this.provider = provider;
    }

    /** 다음 로그인에서 제공자가 돌려줄 프로필. */
    public void willReturn(String providerUserId, String nickname) {
        this.nextProfile = new OAuthProfile(providerUserId, nickname);
        this.failNext = false;
    }

    /** 다음 로그인에서 제공자 호출이 실패하는 상황. */
    public void willFail() {
        this.failNext = true;
    }

    public void reset() {
        this.nextProfile = new OAuthProfile("stub-1", "스텁");
        this.failNext = false;
        this.lastState = null;
        this.lastCallbackUri = null;
    }

    /** authorize 가 제공자에게 넘긴 state — 테스트가 콜백을 흉내 낼 때 쓴다. */
    public String lastState() {
        return lastState;
    }

    public String lastCallbackUri() {
        return lastCallbackUri;
    }

    @Override
    public AuthProvider provider() {
        return provider;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String authorizeUrl(String state, String callbackUri) {
        this.lastState = state;
        this.lastCallbackUri = callbackUri;
        return "https://stub.example/" + provider.name().toLowerCase()
                + "/authorize?state=" + state;
    }

    @Override
    public OAuthProfile fetchProfile(String code, String state, String callbackUri) {
        if (failNext) {
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return nextProfile;
    }
}
