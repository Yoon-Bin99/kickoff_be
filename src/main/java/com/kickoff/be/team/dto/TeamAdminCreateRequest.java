package com.kickoff.be.team.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/teams/{teamId}/admins (계약서 §4-2, v1.9.0).
 *
 * 사용자 id 가 아니라 이메일로 지목한다. 소유자가 아는 건 상대의 이메일이지 내부 id 가
 * 아니기 때문이다. 소셜로만 가입한 계정은 이메일이 null 이라 이 경로로 임명할 수 없다 —
 * 그 경우는 404 로 나가고, 초대 흐름은 v2 다.
 */
public record TeamAdminCreateRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
