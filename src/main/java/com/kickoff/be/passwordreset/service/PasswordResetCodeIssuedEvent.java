package com.kickoff.be.passwordreset.service;

/**
 * 재설정 코드를 보내야 한다 (계약서 §3-3, v1.23.0).
 *
 * 커밋 이후 비동기로 처리된다. 요청 안에서 SMTP 를 기다리면 응답이 그만큼 늦는데,
 * <b>그 지연 차이 자체가 "계정이 있다"는 신호</b>가 된다 — 없는 계정은 즉답이니까.
 *
 * 평문 코드를 싣는다. DB 에는 해시만 있어 여기서 다시 꺼낼 수 없다.
 */
record PasswordResetCodeIssuedEvent(String email, String code) {
}
