package com.kickoff.be.email.client;

/**
 * 메일 발송 어댑터 (계약서 §3-3, v1.23.0). 문자와 같은 구조다.
 *
 * <b>실패를 던져도 된다.</b> 비밀번호 재설정 발송은 비동기라, 여기서 나는 예외는
 * 요청 응답에 닿지 않고 로그로만 남는다 — 계약이 "항상 204"를 요구하기 때문이다.
 * 502 로 나가면 그 자체가 "존재하는 계정"이라는 신호가 된다.
 */
public interface EmailClient {

    void send(String to, String subject, String body);
}
