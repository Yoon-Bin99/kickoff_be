package com.kickoff.be.place.dto;

import java.util.List;

/**
 * 계약서 §5-1 의 응답 봉투. 결과가 없어도 에러가 아니라 빈 배열이다 —
 * "검색 결과 없음"은 정상적인 결과이고, FE 는 그때 직접 입력을 안내한다.
 */
public record PlaceSearchResponse(List<Place> places) {
}
