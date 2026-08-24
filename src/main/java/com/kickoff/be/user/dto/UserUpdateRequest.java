package com.kickoff.be.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/users/me (계약서 §3). 둘 다 optional 이고 형식은 signup 과 같다.
 * 소셜 가입자가 전화번호를 채우는 게 주 용도다 — 전화번호 없이는 팀을 만들 수 없다.
 */
public record UserUpdateRequest(

        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        String nickname,

        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        String phone
) {
}
