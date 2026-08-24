package com.kickoff.be.user.dto;

import jakarta.validation.constraints.Pattern;

/**
 * PUT /api/users/me/push-token (계약서 §8).
 *
 * null 은 <b>등록 해제</b>다 — FE 가 로그아웃할 때 보낸다. 그래서 @NotBlank 를 걸지 않는다.
 * @Pattern 은 null 을 통과시키므로 "값이 있으면 형식 검증, 없으면 해제"가 그대로 성립한다.
 */
public record PushTokenRequest(

        @Pattern(regexp = "^ExponentPushToken\\[.+]$",
                message = "Expo push token 형식이 올바르지 않습니다.")
        String expoPushToken
) {
}
