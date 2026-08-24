package com.kickoff.be.oauth.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인. 키가 없으면 {@link #isConfigured()} 가 false 라 레지스트리에 등록되지 않는다.
 * 응답 구조: 닉네임이 {@code kakao_account.profile.nickname} 에 있다.
 */
@Slf4j
@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String PROFILE_URL = "https://kapi.kakao.com/v2/user/me";
    /**
     * 닉네임만 요청한다. v1.3.4 에서 이메일을 쓰지 않게 돼 동의 항목에서 뺐다 —
     * 사용자에게 필요 없는 동의를 요구하지 않고, 비즈 앱 전환도 필요 없어진다.
     */
    private static final String SCOPE = "profile_nickname";

    private final OAuthProperties.Provider config;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties) {
        this.config = properties.kakao();
        this.restClient = RestClient.create();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public String authorizeUrl(String state, String callbackUri) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + encode(config.clientId())
                + "&redirect_uri=" + encode(callbackUri)
                + "&state=" + encode(state)
                + "&scope=" + encode(SCOPE);
    }

    @Override
    public OAuthProfile fetchProfile(String code, String state, String callbackUri) {
        String accessToken = exchangeToken(code, callbackUri);
        Map<String, Object> body = get(PROFILE_URL, accessToken);

        Object id = body.get("id");
        if (id == null) {
            log.warn("카카오 프로필에 id 가 없다: {}", body.keySet());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        Map<String, Object> account = asMap(body.get("kakao_account"));
        Map<String, Object> profile = asMap(account.get("profile"));
        return new OAuthProfile(String.valueOf(id), (String) profile.get("nickname"));
    }

    private String exchangeToken(String code, String callbackUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("redirect_uri", callbackUri);
        form.add("code", code);
        if (config.hasSecret()) {
            form.add("client_secret", config.clientSecret());
        }
        Map<String, Object> body = post(TOKEN_URL, form);
        String accessToken = (String) body.get("access_token");
        if (accessToken == null) {
            log.warn("카카오 토큰 교환 실패: {}", body);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return accessToken;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        try {
            return restClient.post().uri(url).body(form).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("카카오 호출 실패 {}: {}", url, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String url, String accessToken) {
        try {
            return restClient.get().uri(url)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("카카오 호출 실패 {}: {}", url, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
