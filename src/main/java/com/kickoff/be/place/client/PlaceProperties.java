package com.kickoff.be.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param restKey 카카오 로컬 API REST 키. <b>로그인용 키와 다른 앱의 키다</b> (계약서 §5-1) —
 *                kickoff 앱은 카카오맵을 새로 켜려면 결제수단 등록이 필요해서, 이미 켜져 있는
 *                다른 앱의 키를 쓴다. 환경변수 이름도 KAKAO_MAP_REST_KEY 로 따로다
 */
@ConfigurationProperties(prefix = "place.kakao")
public record PlaceProperties(String restKey) {

    public boolean isConfigured() {
        return restKey != null && !restKey.isBlank();
    }
}
