package com.kickoff.be.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 인증번호 확인 요청 (계약서 §3-2). */
public record PhoneVerificationConfirmRequest(

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        String phone,

        /**
         * 형식만 본다. 맞는지 틀리는지는 서비스가 답한다 — 여기서 400 VALIDATION_FAILED 로
         * 걸러 버리면 계약이 정한 VERIFICATION_CODE_MISMATCH 가 나갈 자리가 없어진다.
         */
        @NotBlank(message = "인증번호는 필수입니다.")
        @Pattern(regexp = "^\\d{6}$", message = "인증번호는 6자리 숫자여야 합니다.")
        String code
) {
}
