package com.kickoff.be.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/refresh 요청 (계약서 §3, v1.7.0). */
public record RefreshRequest(

        @NotBlank(message = "refresh token 은 필수입니다.")
        String refreshToken
) {
}
