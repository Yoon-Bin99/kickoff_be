package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Position;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/teams/{teamId}/members (계약서 §4-1, v1.8.0). */
public record TeamMemberCreateRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @Size(min = 1, max = 20, message = "이름은 1~20자여야 합니다.")
        String name,

        /** 포지션을 안 정한 팀원이 있다. */
        Position position,

        /** 등번호가 없는 팀원이 있다. 있으면 0~99. */
        @Min(value = 0, message = "등번호는 0 이상이어야 합니다.")
        @Max(value = 99, message = "등번호는 99 이하여야 합니다.")
        Integer backNumber
) {
}
