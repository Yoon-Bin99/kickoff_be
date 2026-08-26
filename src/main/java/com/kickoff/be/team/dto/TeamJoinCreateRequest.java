package com.kickoff.be.team.dto;

import jakarta.validation.constraints.Size;

/** POST /api/teams/{teamId}/join (계약서 §4-3, v1.11.0). 메시지는 optional. */
public record TeamJoinCreateRequest(

        @Size(max = 200, message = "메시지는 200자를 넘을 수 없습니다.")
        String message
) {
}
