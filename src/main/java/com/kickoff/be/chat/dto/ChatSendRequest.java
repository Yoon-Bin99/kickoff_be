package com.kickoff.be.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/requests/{requestId}/chat (계약서 §6-1, v1.12.0). */
public record ChatSendRequest(

        /** 공백만인 것은 안 된다 — @NotBlank 가 trim 후 비면 걸러낸다. */
        @NotBlank(message = "내용은 필수입니다.")
        @Size(min = 1, max = 500, message = "내용은 1~500자여야 합니다.")
        String content
) {
}
