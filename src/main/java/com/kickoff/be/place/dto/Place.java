package com.kickoff.be.place.dto;

/**
 * 장소 검색 결과 한 건 (계약서 §5-1). 카카오 응답을 그대로 흘려보내지 않고 이 형태로 좁힌다 —
 * FE 가 필요한 건 이름·주소·좌표뿐이고, 제공자 응답 구조가 FE 까지 새면 나중에 못 바꾼다.
 *
 * @param roadAddress 도로명 주소. <b>nullable</b> — 카카오는 없을 때 null 이 아니라 빈 문자열을
 *                    주는데, 실제 응답 첫 건부터 그렇게 온다. 여기서 null 로 정규화한다
 */
public record Place(
        String name,
        String address,
        String roadAddress,
        double latitude,
        double longitude
) {
}
