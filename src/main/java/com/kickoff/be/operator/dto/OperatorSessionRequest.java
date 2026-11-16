package com.kickoff.be.operator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 인증 앱이 보여 주는 6자리 (계약서 §7-2). */
public record OperatorSessionRequest(

        @NotBlank(message = "일회용 비밀번호를 입력해 주세요.")
        @Pattern(regexp = "[0-9]{6}", message = "6자리 숫자여야 합니다.")
        String otp
) {
}
