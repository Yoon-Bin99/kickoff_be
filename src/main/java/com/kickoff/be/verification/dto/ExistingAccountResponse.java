package com.kickoff.be.verification.dto;

/**
 * 이 번호로 이미 가입된 계정의 안내 (계약서 §3-2, v1.17.0).
 *
 * <b>번호 소유를 코드로 증명한 사람에게만</b> 나간다. 그래서 발송이 아니라 확인 응답에
 * 붙는다 — 남의 번호를 넣어 "이 사람이 가입했나, 무엇으로 했나"를 캐려면 그 번호로 온
 * 문자를 읽어야 한다.
 *
 * availability 의 phone 은 여전히 "사용 가능/불가"만 답한다. 거기서 수단까지 알려주면
 * 인증 없이 캐낼 수 있게 된다.
 *
 * @param method      EMAIL / KAKAO / NAVER. 이메일이 있으면 EMAIL, 아니면 첫 provider
 * @param maskedEmail EMAIL 일 때만. 아니면 null
 */
public record ExistingAccountResponse(String method, String maskedEmail) {
}
