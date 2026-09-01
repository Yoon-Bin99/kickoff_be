package com.kickoff.be.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 계약서 §7-1 — 1~500자. */
public record SupportSendRequest(

        @NotBlank(message = "내용은 필수입니다.")
        @Size(min = 1, max = 500, message = "내용은 1~500자여야 합니다.")
        String content
) {
}
