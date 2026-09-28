package com.kickoff.be.stadium.service;

import com.kickoff.be.stadium.client.SeoulReservationRow;

/**
 * 공공 API 한 행을 구장 필드로 옮기는 규칙 (계약서 §8-1, v1.27.0).
 *
 * 따로 떼어 둔 것은 <b>여기가 조용히 틀리는 자리</b>이기 때문이다. 지역 문자열이 "성동구"로
 * 들어가도 타입도 형식도 멀쩡해서 저장까지 아무 문제가 없고, 지역 필터 "서울"로 찾을 때에야
 * 공공 구장이 하나도 안 나오는 것으로 드러난다. 순수 함수로 두면 테스트가 직접 찌를 수 있다.
 *
 * <b>주소는 여기서 만들 수 없다.</b> 공공 API 응답에 주소 필드가 없다(장소명 PLACENM 과
 * 좌표 X·Y 뿐이고, 좌표는 §9 로 v1 범위 밖이다). 그래서 SEOUL_PUBLIC 의 address 는 null 이다.
 */
final class StadiumFields {

    private StadiumFields() {
    }

    /** SVCNM 이 예약 서비스 이름이다. 같은 장소도 서비스마다 다른 이름을 갖는다. */
    static String name(SeoulReservationRow row) {
        return row.svcName() == null ? row.svcId() : row.svcName();
    }

    /**
     * AREANM 은 <b>"성동구"처럼 자치구만</b> 온다. 계약서 §8-1 의 지역 문자열은 모집글 §5 와
     * 같은 "서울 성동구" 형식이라 시 이름을 붙인다 — 안 붙이면 지역 필터 "서울"이 공공
     * 구장을 하나도 못 찾는다(프리픽스 매칭이라 더 그렇다).
     */
    static String region(SeoulReservationRow row) {
        String area = row.areaName();
        if (area == null || area.isBlank()) {
            return "서울";
        }
        return area.startsWith("서울") ? area : "서울 " + area;
    }

    /** SVCURL 이 이미 예약 페이지 주소다. 비어 있을 때만 SVCID 로 만든다 (계약서 §8-1). */
    static String url(SeoulReservationRow row) {
        if (row.url() != null) {
            return row.url();
        }
        return "https://yeyak.seoul.go.kr/web/reservation/selectReservView.do?rsv_svc_id="
                + row.svcId();
    }

    /**
     * "2026-09-01 ~ 2026-12-31" 형태로 만든다.
     *
     * 원문은 {@code "2025-12-01 00:00:00.0"} 처럼 시분초가 붙어 오는데, 이용 기간에
     * 00:00:00.0 은 정보가 아니라 잡음이라 날짜만 남긴다. 형식이 예상과 다르면 <b>자르지
     * 않고 그대로</b> 둔다 — 파싱 실패로 동기화를 멈추느니 원문을 보여 주는 게 낫다.
     */
    static String usePeriod(SeoulReservationRow row) {
        String begin = datePart(row.openBegin());
        String end = datePart(row.openEnd());
        if (begin == null && end == null) {
            return null;
        }
        return (begin == null ? "" : begin) + " ~ " + (end == null ? "" : end);
    }

    private static String datePart(String value) {
        if (value == null) {
            return null;
        }
        return value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-'
                ? value.substring(0, 10)
                : value;
    }
}
