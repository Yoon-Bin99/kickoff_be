package com.kickoff.be.holiday.client;

/**
 * 공휴일 조회를 <b>완전히 받아 오지 못했다</b>는 뜻 (계약서 §8-2).
 *
 * {@code BusinessException} 이 아닌 이유: 이 실패가 사용자 응답을 실패로 만들지 않는다.
 * 서비스가 받아서 {@code source: NONE} 으로 바꿔 200 을 내린다 — 계약서가 "실패 시
 * 서버 500 금지"로 정했다. 에러 코드를 붙이면 밖으로 나가지 않을 코드가 하나 생긴다.
 */
public class HolidayLookupException extends RuntimeException {

    public HolidayLookupException(String message) {
        super(message);
    }
}
