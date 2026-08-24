package com.kickoff.be.oauth.controller;

import com.kickoff.be.oauth.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 리다이렉트 흐름 (계약서 §3-1). 두 엔드포인트 모두 JSON 이 아니라 302 를 낸다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    /** FE 가 브라우저로 여는 진입점. 인증 불필요. */
    @GetMapping("/api/auth/oauth/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider,
                                          @RequestParam String redirect,
                                          HttpServletRequest request) {
        // 성공한 요청은 어디에도 흔적이 안 남아, 사후에 "요청이 오긴 했는지"조차 알 수 없었다.
        log.info("소셜 로그인 진입 — provider={}, origin={}, redirect={}",
                provider, originOf(request), redirect);
        URI location = oauthService.authorizeUri(provider, redirect, originOf(request));
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /** 제공자가 되돌려 보내는 자리. FE 는 직접 호출하지 않는다. */
    @GetMapping("/api/auth/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        // code 와 state 는 값 자체가 인증 재료라 유무만 남긴다.
        log.info("소셜 로그인 콜백 — provider={}, code={}, state={}, error={}",
                provider, code == null ? "없음" : "있음", state == null ? "없음" : "있음", error);
        URI location = oauthService.completeLogin(provider, code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /**
     * 콜백 URI 를 만들 오리진. authorize 는 FE 가 직접 부르는 요청이라 여기서 뽑아도 되고,
     * 그래야 localhost 와 LAN IP 양쪽에서 같은 코드로 동작한다. 뽑은 값은 state 에 실려
     * 콜백까지 전달된다 — 콜백은 제공자가 부르는 요청이라 Host 를 믿지 않는다.
     */
    private static String originOf(HttpServletRequest request) {
        StringBuilder origin = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }
}
