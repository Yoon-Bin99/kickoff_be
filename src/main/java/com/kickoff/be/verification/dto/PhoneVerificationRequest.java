package com.kickoff.be.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 인증번호 발송 요청 (계약서 §3-2). 형식 검증은 signup 과 같아야 한다. */
public record PhoneVerificationRequest(

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        String phone
) {
}
