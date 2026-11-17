package com.kickoff.be.weather.service;

/**
 * 표 한 줄 — 어느 지역을 어느 예보 지점으로 보는가 (계약서 §8-3).
 *
 * {@code sigungu} 가 비어 있으면 그 시/도의 <b>대표 지점</b>이다(시청·도청 소재지).
 * 시군구를 표에서 못 찾았을 때 대신 쓴다.
 *
 * {@code midLandRegId}·{@code midTaRegId} 는 비어 있을 수 있다(null). 공식 코드표를
 * 확인하지 못한 자리라 지어내지 않고 비워 둔 것이고, 비어 있으면 그 지역은 중기예보
 * 없이 단기예보만 나간다 — 표가 채워지면 코드 수정 없이 중기도 돈다.
 */
public record WeatherArea(
        String sido,
        String sigungu,
        int nx,
        int ny,
        String midLandRegId,
        String midTaRegId
) {

    /** 시/도 대표 지점인가 — 화면에 시/도만 보여야 하는 줄이다 (계약서 §8-3 `area`). */
    public boolean isProvinceFallback() {
        return sigungu == null || sigungu.isBlank();
    }

    /** 응답의 {@code area} — "서울 강서구", 대표 지점이면 "서울". */
    public String displayName() {
        return isProvinceFallback() ? sido : sido + " " + sigungu;
    }

    public String gridKey() {
        return nx + "," + ny;
    }

    public boolean hasMidTerm() {
        return midLandRegId != null && midTaRegId != null;
    }
}
