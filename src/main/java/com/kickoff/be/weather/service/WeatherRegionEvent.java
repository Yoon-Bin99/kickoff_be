package com.kickoff.be.weather.service;

/**
 * 이 지역의 예보가 지금 필요해졌다 (계약서 §8-3).
 *
 * 글이 새로 생기거나 {@code matchAt}·{@code region} 이 바뀌었을 때 글 쪽에서 띄운다.
 * 받아 오기는 커밋 뒤에 비동기로 일어나므로 <b>그 요청의 응답에는 날씨가 없을 수 있다</b> —
 * 계약서가 그렇게 못박았다. 다음 조회부터 보인다.
 *
 * 값만 싣는다(다른 푸시 이벤트와 같은 규칙) — 커밋 뒤에 실행되므로 엔티티를 들고 있으면
 * 세션이 없다.
 */
public record WeatherRegionEvent(String region) {
}
