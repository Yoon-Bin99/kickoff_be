package com.kickoff.be.verification.dto;

/** 확인 성공 응답 (계약서 §3-2). 10분 유효, 1회용, 그 번호에 묶인다. */
public record VerificationTokenResponse(String verificationToken) {
}
