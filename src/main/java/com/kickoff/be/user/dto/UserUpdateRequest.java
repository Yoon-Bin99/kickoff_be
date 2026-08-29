package com.kickoff.be.user.dto;

import com.kickoff.be.common.Patchable;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/users/me (계약서 §3). 전부 optional 이고 형식은 signup 과 같다.
 * 소셜 가입자가 전화번호와 활동 지역을 채우는 게 주 용도다 — 전화번호 없이는 팀을 만들 수 없다.
 *
 * {@link Patchable} 로 받는 이유는 "안 보냄"과 "명시적 null"을 갈라야 하기 때문이다
 * (v1.5.1 공통 규칙). 지울 수 있는 건 활동 지역뿐이고, 닉네임·전화번호에 null 을 보내면 400 이다.
 */
public record UserUpdateRequest(

        // ── 지울 수 없는 필드

        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        Patchable<String> nickname,

        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        Patchable<String> phone,

        // ── 지울 수 있는 필드 (계약서 §2, v1.6.0)

        /** null 이면 활동 지역 없음 = 전국으로 되돌린다. */
        @Size(max = 20, message = "활동 지역은 20자를 넘을 수 없습니다.")
        Patchable<String> activityRegion,

        // ── 필드가 아니라 자격 증명

        /**
         * 전화번호 인증 토큰 (계약서 §3-2, v1.15.0). phone 을 넣거나 바꾸는 요청에만 쓴다.
         *
         * 여기만 {@link Patchable} 이 아닌 게 의도다. 이건 사용자의 속성이 아니라 이번
         * 요청에 딸려 온 증명이라, "안 보냄"과 "명시적 null"을 가를 일이 없다.
         */
        String verificationToken
) {
}
