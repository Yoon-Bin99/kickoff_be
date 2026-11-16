package com.kickoff.be.place.dto;

/**
 * 장소 검색 결과 한 건 (계약서 §5-1, v1.37.0·v1.37.1). 카카오 응답을 그대로 흘려보내지
 * 않고 이 형태로 좁힌다 — 제공자 응답 구조가 FE 까지 새면 나중에 못 바꾼다.
 *
 * <b>좌표를 싣지 않는다</b>(v1.37.1). 고르는 화면은 이름·주소만 보이고, 미리보기 지도는
 * 기기가 장소 ID 로 다시 조회해 그린다 — 내려 주지 않으면 실수로 저장될 여지도 없다.
 *
 * <b>이 응답은 어디에도 저장하지 않는다</b>(DB·캐시·로그). 카카오 로컬 검색의 응답은
 * 저장할 수 없다는 것이 §5-1 전체의 전제다.
 *
 * @param id          카카오 장소 ID. 이것만이 저장해도 되는 값이다 (글의 kakaoPlaceId)
 * @param roadAddress 도로명 주소. <b>nullable</b> — 카카오는 없을 때 null 이 아니라 빈 문자열을
 *                    주는데, 실제 응답 첫 건부터 그렇게 온다. 여기서 null 로 정규화한다
 */
public record Place(
        String id,
        String name,
        String address,
        String roadAddress
) {
}
