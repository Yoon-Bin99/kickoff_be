package com.kickoff.be.weather.client;

/**
 * 같은 기상청 자료를 받아 오는 두 입구 (계약서 §8-3, v1.38.2).
 *
 * 경로와 응답 형식이 같고 <b>인증키를 싣는 인자 이름과 호스트만 다르다</b>. 그래서
 * 설정값 하나로 갈아 끼울 수 있게 열거형으로 둔다 — 공공데이터포털이 멈췄을 때의
 * 예비 경로다(2025년에 포털 전체가 한 달 가까이 멈춘 전례가 있다).
 *
 * <b>키는 입구마다 따로 받아야 한다.</b> 바꿔 쓰려면 {@code KMA_SERVICE_KEY} 도 그 입구의
 * 키로 바꿔야 한다. API허브는 서비스마다 활용신청을 눌러야 열린다.
 */
public enum KmaProvider {

    /** 공공데이터포털 — 기본. 서비스별 하루 10,000건(개발 계정). */
    DATA_GO_KR("https://apis.data.go.kr/1360000/", "ServiceKey"),

    /** 기상청 API허브 — 예비. 계정 전체 20,000건. */
    APIHUB("https://apihub.kma.go.kr/api/typ02/openApi/", "authKey");

    private final String baseUrl;
    private final String keyParam;

    KmaProvider(String baseUrl, String keyParam) {
        this.baseUrl = baseUrl;
        this.keyParam = keyParam;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String keyParam() {
        return keyParam;
    }
}
