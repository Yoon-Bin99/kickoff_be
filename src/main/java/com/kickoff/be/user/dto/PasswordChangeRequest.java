package com.kickoff.be.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 상태의 비밀번호 변경 (계약서 §3-3 아래, v1.24.0).
 *
 * {@code currentPassword} 에는 형식 규칙을 걸지 않는다 — 기존 비밀번호는 v1.16.0 규칙
 * 이전에 만들어졌을 수 있고, 여기서 묻는 건 "맞는가"뿐이다. 형식으로 먼저 거절하면
 * 옛 규칙으로 가입한 사람이 <b>비밀번호를 바꿀 수도 없게</b> 된다.
 */
public record PasswordChangeRequest(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "비밀번호는 영문·숫자·특수문자를 각각 1개 이상 포함해야 합니다.")
        String newPassword
) {
}
