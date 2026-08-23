package com.kickoff.be.matchrequest.dto;

import jakarta.validation.constraints.Size;

public record RequestCreateRequest(

        @Size(max = 500, message = "메시지는 500자를 넘을 수 없습니다.")
        String message
) {
}
