package com.kickoff.be.oauth.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.client.OAuthClient;
import com.kickoff.be.oauth.client.OAuthClientRegistry;
import com.kickoff.be.oauth.client.OAuthProperties;
import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.oauth.service.OAuthStateStore.PendingLogin;
import com.kickoff.be.oauth.service.SocialLoginService.SocialLoginResult;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 리다이렉트 흐름의 조립 (계약서 §3-1). 제공자와의 왕복은 OAuthClient 가, 계정 결정은
 * SocialLoginService 가 맡고, 여기서는 state 를 걸고 어디로 302 할지만 정한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private static final String CALLBACK_PATH_FORMAT = "/api/auth/oauth/%s/callback";

    private final OAuthClientRegistry clients;
    private final OAuthStateStore stateStore;
    private final RedirectAllowList redirectAllowList;
    private final SocialLoginService socialLoginService;
    private final OAuthProperties properties;

    /**
     * 사용자를 제공자 로그인 화면으로 보낸다.
     *
     * <b>복귀 주소를 제공자보다 먼저 검증한다.</b> 신뢰할 수 없는 입력을 먼저 걸러내는 게
     * 순서로도 맞고, 제공자 검사가 앞서면 키가 없는 동안 허용 목록 위반이 전부
     * UNSUPPORTED_PROVIDER 로 덮여 FE 가 그 실패를 관측할 수 없다.
     */
    public URI authorizeUri(String providerPath, String redirect, String requestBaseUrl) {
        String safeRedirect = redirectAllowList.require(redirect);
        AuthProvider provider = AuthProvider.fromPath(providerPath);
        OAuthClient client = clients.get(provider);
        String callbackUri = callbackUri(provider, requestBaseUrl);
        String state = stateStore.issue(provider, safeRedirect, callbackUri);
        return URI.create(client.authorizeUrl(state, callbackUri));
    }

    /**
     * 제공자가 되돌려 보낸 요청을 처리하고 앱으로 302 한다.
     *
     * 성공이든 실패든 앱으로 돌려보내는 게 계약이지만, <b>state 를 못 찾으면 그럴 수 없다</b> —
     * 복귀 주소가 state 에 들어 있어서다. 그 경우에만 JSON 에러로 끝낸다. 임의의 redirect
     * 파라미터를 믿고 302 하면 open redirect 가 열린다.
     */
    public URI completeLogin(String providerPath, String code, String state,
                             String providerError) {
        AuthProvider provider = AuthProvider.fromPath(providerPath);
        PendingLogin pending = stateStore.consume(state)
                .orElseThrow(() -> new BusinessException(ErrorCode.OAUTH_FAILED));
        if (pending.provider() != provider) {
            // state 는 맞는데 경로의 제공자가 다르다 — 정상 흐름에서는 나올 수 없다
            throw new BusinessException(ErrorCode.OAUTH_FAILED);
        }

        if (providerError != null && !providerError.isBlank()) {
            log.info("제공자가 인증을 거부했다 — provider={}, error={}", provider, providerError);
            return failure(pending.redirect(), ErrorCode.OAUTH_FAILED);
        }
        if (code == null || code.isBlank()) {
            return failure(pending.redirect(), ErrorCode.OAUTH_FAILED);
        }

        try {
            OAuthProfile profile = clients.get(provider)
                    .fetchProfile(code, state, pending.callbackUri());
            SocialLoginResult result = socialLoginService.login(provider, profile);
            return success(pending.redirect(), result);
        } catch (BusinessException e) {
            return failure(pending.redirect(), e.getErrorCode());
        } catch (RuntimeException e) {
            log.warn("소셜 로그인 처리 실패 — provider={}", provider, e);
            return failure(pending.redirect(), ErrorCode.OAUTH_FAILED);
        }
    }

    private URI success(String redirect, SocialLoginResult result) {
        return UriComponentsBuilder.fromUriString(redirect)
                .queryParam("token", result.accessToken())
                .queryParam("isNewUser", result.newUser())
                .build()
                .toUri();
    }

    private URI failure(String redirect, ErrorCode code) {
        return UriComponentsBuilder.fromUriString(redirect)
                .queryParam("error", code.name())
                .build()
                .toUri();
    }

    /**
     * 제공자 콘솔에 등록된 콜백 주소를 만든다. 개발 중에는 localhost 와 LAN IP 양쪽으로
     * 들어오고 LAN IP 는 바뀌므로, 설정이 없으면 authorize 요청의 오리진을 그대로 쓴다.
     * 이렇게 만든 값은 state 에 실려 콜백까지 간다 — 콜백은 요청에서 다시 뽑지 않는다.
     */
    private String callbackUri(AuthProvider provider, String requestBaseUrl) {
        String base = properties.callbackBaseUrl();
        if (base == null || base.isBlank()) {
            base = requestBaseUrl;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + CALLBACK_PATH_FORMAT.formatted(provider.name().toLowerCase(Locale.ROOT));
    }
}
