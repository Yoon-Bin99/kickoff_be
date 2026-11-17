package com.kickoff.be.weather.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 날씨 설정 (계약서 §8-3).
 *
 * @param serviceKey env {@code KMA_SERVICE_KEY}. <b>비밀값</b> — 저장소·로그·FE 금지.
 *                   공공데이터포털의 "일반 인증키(Decoding)" 를 그대로 넣는다(주소에 넣을
 *                   때 BE 가 인코딩한다). 없으면 받아 오기 작업을 돌리지 않고 모든
 *                   {@code weather} 가 null 이다 — 기동과 다른 API 는 정상이다.
 * @param provider   받아 오는 곳. 기본 {@code DATA_GO_KR}.
 * @param dailyLimit 하루 호출 한도. 절반을 넘으면 경고, 90%를 넘으면 그날은 더 부르지 않는다.
 * @param fake       개발용 가짜 값. <b>dev 프로필에서만</b> 듣는다 — 운영에서 켜지면
 *                   지어낸 예보를 기상청 예보처럼 보여 주게 된다(기상법 제17조).
 */
@ConfigurationProperties(prefix = "kickoff.weather")
public record WeatherProperties(
        String serviceKey,
        KmaProvider provider,
        Integer dailyLimit,
        boolean fake
) {

    private static final int DEFAULT_DAILY_LIMIT = 10_000;

    public WeatherProperties {
        provider = provider == null ? KmaProvider.DATA_GO_KR : provider;
        dailyLimit = dailyLimit == null || dailyLimit <= 0 ? DEFAULT_DAILY_LIMIT : dailyLimit;
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
