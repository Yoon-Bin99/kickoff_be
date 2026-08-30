package com.kickoff.be.auth.dto;

/**
 * 가입 화면이 따라야 할 서버 정책 (계약서 §3-2, v1.16.1).
 *
 * @param phoneVerificationRequired 서버가 전화번호 인증을 <b>실제로 강제하는지</b>.
 *                                  PHONE_VERIFICATION_REQUIRED 의 현재값이다.
 */
public record SignupPolicyResponse(boolean phoneVerificationRequired) {
}
