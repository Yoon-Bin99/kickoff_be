package com.kickoff.be.oauth.client;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 설정. 클라이언트 키는 <b>환경변수로만</b> 들어온다 —
 * application.yaml 에는 `${KAKAO_CLIENT_ID:}` 처럼 바인딩만 있고 값은 없다.
 *
 * @param allowedRedirects 로그인 완료 후 돌아갈 수 있는 URL 패턴 (open redirect 방지, 계약서 §3-1)
 * @param callbackBaseUrl  제공자에 넘길 콜백의 오리진. 비우면 요청에서 뽑아 쓴다 —
 *                         개발 중에는 localhost 와 LAN IP 양쪽으로 들어오고 LAN IP 는 바뀐다
 */
@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        Provider kakao,
        Provider naver,
        List<String> allowedRedirects,
        String callbackBaseUrl
) {

    public OAuthProperties {
        kakao = kakao == null ? Provider.EMPTY : kakao;
        naver = naver == null ? Provider.EMPTY : naver;
        allowedRedirects = allowedRedirects == null ? List.of() : allowedRedirects;
    }

    public record Provider(String clientId, String clientSecret) {

        public static final Provider EMPTY = new Provider(null, null);

        /**
         * client-id 만 있으면 쓸 수 있다고 본다. 카카오는 client_secret 이 선택이라
         * 둘 다 요구하면 정상 설정을 미설정으로 오판한다.
         */
        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }

        public boolean hasSecret() {
            return clientSecret != null && !clientSecret.isBlank();
        }
    }
}
