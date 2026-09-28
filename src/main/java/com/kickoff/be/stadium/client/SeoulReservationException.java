package com.kickoff.be.stadium.client;

/**
 * 서울 공공서비스예약 API 를 <b>완전히 받아 오지 못했다</b>는 뜻 (계약서 §8-1, v1.27.0).
 *
 * {@code BusinessException} 이 아닌 이유: 사용자 요청에 대한 응답이 아니다. 동기화는
 * 스케줄로만 돌고, 실패는 로그로 끝나며 사용자에게는 <b>마지막 성공 데이터가 그대로</b>
 * 보인다. 에러 코드를 붙이면 계약서에 없는 코드가 생기고, 붙일 자리도 없다.
 */
public class SeoulReservationException extends RuntimeException {

    public SeoulReservationException(String message) {
        super(message);
    }
}
