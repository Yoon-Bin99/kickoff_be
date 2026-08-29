package com.kickoff.be.sms.client;

/**
 * 문자 발송 경계 (계약서 §3-2, v1.15.0). PushClient·OAuthClient 와 같은 이유로 둔다 —
 * 테스트가 실제 제공자를 때리지 않고 무엇이 어디로 나갔는지 볼 수 있어야 한다.
 *
 * <b>푸시와 달리 실패를 삼키지 않는다.</b> 푸시는 best-effort 라 못 가도 원 요청이
 * 성공해야 하지만, 인증번호는 그것 자체가 요청의 목적이다. 못 보냈는데 204 를 주면
 * 사용자는 오지 않는 문자를 기다린다 — 계약이 발송 실패에 502 SMS_SEND_FAILED 를
 * 따로 둔 이유다.
 */
public interface SmsClient {

    /**
     * 한 건 발송. 실패하면 예외를 던진다 (호출자가 502 로 바꾼다).
     *
     * @param phone {@code 010-0000-0000} 형식
     * @param text  본문 전체
     */
    void send(String phone, String text);
}
