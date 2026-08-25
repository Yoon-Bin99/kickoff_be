package com.kickoff.be.team.dto;

import com.kickoff.be.common.Patchable;
import com.kickoff.be.team.entity.Position;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/teams/{teamId}/members/{memberId} (계약서 §4-1, v1.8.0).
 *
 * 전부 optional 이고 v1.5.1 지우기 규칙을 탄다 — 포지션·등번호는 명시적 null 로 지울 수
 * 있고, 이름은 빈 상태가 의미를 갖지 않아 null 을 보내면 400 이다.
 */
public record TeamMemberUpdateRequest(

        @Size(min = 1, max = 20, message = "이름은 1~20자여야 합니다.")
        Patchable<String> name,

        Patchable<Position> position,

        @Min(value = 0, message = "등번호는 0 이상이어야 합니다.")
        @Max(value = 99, message = "등번호는 99 이하여야 합니다.")
        Patchable<Integer> backNumber
) {
}
