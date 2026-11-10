package com.kickoff.be.holiday.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 톡캘린더 어드민 키 (계약서 §8-2).
 *
 * @param adminKey 카카오 개발자 콘솔 › 앱 키 › <b>Admin 키</b>. 환경변수
 *                 {@code KAKAO_ADMIN_KEY}. 헤더 {@code Authorization: KakaoAK {키}} 로
 *                 보낸다.
 *                 <b>비밀값이다</b> — 앱 번들·로그·저장소에 넣지 않는다. 로그인에 쓰는
 *                 REST API 키({@code KAKAO_CLIENT_ID})와 다른 값이고, 이쪽은 서버 전용이다.
 *                 <b>없으면 모든 해가 source NONE 으로 나간다</b> — 기동도 다른 API 도
 *                 막히지 않는다(FE 가 자체 계산값으로 폴백한다)
 */
@ConfigurationProperties(prefix = "holiday.kakao")
public record HolidayProperties(String adminKey) {

    public boolean isConfigured() {
        return adminKey != null && !adminKey.isBlank();
    }
}
