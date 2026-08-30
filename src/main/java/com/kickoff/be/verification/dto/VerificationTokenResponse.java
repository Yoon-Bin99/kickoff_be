package com.kickoff.be.verification.dto;

/**
 * 확인 성공 응답 (계약서 §3-2). 토큰은 10분 유효, 1회용, 그 번호에 묶인다.
 *
 * {@code existingAccount} 는 그 번호로 가입된 계정이 없으면 null 이다. <b>키 자체는 항상
 * 있어야 한다</b> — 이 프로젝트의 Jackson 이 default-property-inclusion: always 라 null
 * 도 키로 나가므로 그대로 계약을 만족한다.
 */
public record VerificationTokenResponse(String verificationToken,
                                        ExistingAccountResponse existingAccount) {
}
