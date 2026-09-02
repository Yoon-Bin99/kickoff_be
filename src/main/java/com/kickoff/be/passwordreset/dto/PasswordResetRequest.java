package com.kickoff.be.passwordreset.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 계약서 §3-3. <b>형식 오류만 400</b>이다 — 계정이 없는 것은 오류가 아니라 204 다.
 */
public record PasswordResetRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
