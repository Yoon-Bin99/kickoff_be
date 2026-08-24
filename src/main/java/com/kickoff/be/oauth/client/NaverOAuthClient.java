package com.kickoff.be.oauth.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 네이버 로그인. 카카오와 달리 프로필이 {@code response} 아래에 평평하게 들어 있고,
 * 토큰 교환에 state 를 함께 넘겨야 한다.
 */
@Slf4j
@Component
public class NaverOAuthClient implements OAuthClient {

    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String PROFILE_URL = "https://openapi.naver.com/v1/nid/me";

    private final OAuthProperties.Provider config;
    private final RestClient restClient;

    public NaverOAuthClient(OAuthProperties properties) {
        this.config = properties.naver();
        this.restClient = RestClient.create();
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.NAVER;
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
                + "&state=" + encode(state);
    }

    @Override
    public OAuthProfile fetchProfile(String code, String state, String callbackUri) {
        String accessToken = exchangeToken(code, state);
        Map<String, Object> body = get(PROFILE_URL, accessToken);
        Map<String, Object> profile = asMap(body.get("response"));

        Object id = profile.get("id");
        if (id == null) {
            log.warn("네이버 프로필에 id 가 없다: {}", body);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        // 네이버는 nickname 이 비어 있는 계정이 있어 name 으로 물러난다
        String nickname = (String) profile.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            nickname = (String) profile.get("name");
        }
        return new OAuthProfile(String.valueOf(id), (String) profile.get("email"), nickname);
    }

    private String exchangeToken(String code, String state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code", code);
        form.add("state", state);
        Map<String, Object> body = post(TOKEN_URL, form);
        String accessToken = (String) body.get("access_token");
        if (accessToken == null) {
            log.warn("네이버 토큰 교환 실패: {}", body);
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }
        return accessToken;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        try {
            return restClient.post().uri(url).body(form).retrieve().body(Map.class);
        } catch (RuntimeException e) {
            log.warn("네이버 호출 실패 {}: {}", url, e.getMessage());
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
            log.warn("네이버 호출 실패 {}: {}", url, e.getMessage());
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
