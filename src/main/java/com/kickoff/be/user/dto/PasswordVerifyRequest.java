package com.kickoff.be.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 본인 확인용 비밀번호 (계약서 §3-4 위, v1.24.1).
 *
 * <b>형식 규칙이 없는 게 의도다.</b> v1.16.0 이전 규칙으로 만들어진 계정이 실재하는데,
 * 형식으로 먼저 거절하면 그 사람들은 계정 관리 화면에 <b>들어갈 수조차 없다</b> —
 * 비밀번호를 바꾸러 가는 길이 비밀번호 규칙에 막히는 셈이다.
 * 여기서 묻는 것은 "맞는가"뿐이다.
 */
public record PasswordVerifyRequest(

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
