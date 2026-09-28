package com.kickoff.be.stadium.client;

/**
 * 서울 공공서비스예약 API 한 건 (계약서 §8-1, v1.27.0).
 *
 * <b>필드 이름은 실제 응답에서 확인한 것이다</b> — {@code ListPublicReservationSport} 를
 * 샘플키로 불러 받은 응답에 SVCID·SVCNM·AREANM·SVCSTATNM·SVCURL·MINCLASSNM·
 * SVCOPNBGNDT·SVCOPNENDDT 가 그대로 있었다. 응답에 <b>주소 필드는 없다</b>(PLACENM 과
 * 좌표 X·Y 만 있다).
 *
 * 한 구장이 한 행이 아니다. "테니스장1(평일)"과 "테니스장1(토/일/공휴일)"처럼 <b>같은
 * 장소가 예약 서비스 단위로 여러 행</b>이 된다. 계약서가 SVCID 를 갱신 기준으로 정했으니
 * 그 단위를 그대로 따른다 — 장소로 묶으면 예약 링크가 한 행에 여럿 붙어 "링크만 건다"는
 * 이 기능의 전제가 깨진다.
 */
public record SeoulReservationRow(
        String svcId,
        String svcName,
        String areaName,
        String minClassName,
        String statusName,
        String url,
        String openBegin,
        String openEnd
) {
}
