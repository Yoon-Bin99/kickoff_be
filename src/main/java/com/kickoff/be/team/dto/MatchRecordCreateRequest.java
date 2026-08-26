package com.kickoff.be.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/requests/{requestId}/record (계약서 §4-1, v1.10.0).
 *
 * 스코어와 메모만 받는다. 상대 팀 이름과 경기 날짜는 서버가 매칭에서 유도한다 —
 * 클라이언트가 보낸 값을 믿으면 "매칭에서 만든 기록"인데 상대가 엉뚱한 팀일 수 있다.
 */
public record MatchRecordCreateRequest(

        @NotNull(message = "our 점수는 필수입니다.")
        @Min(value = 0, message = "점수는 0 이상이어야 합니다.")
        @Max(value = 99, message = "점수는 99 이하여야 합니다.")
        Integer ourScore,

        @NotNull(message = "상대 점수는 필수입니다.")
        @Min(value = 0, message = "점수는 0 이상이어야 합니다.")
        @Max(value = 99, message = "점수는 99 이하여야 합니다.")
        Integer opponentScore,

        @Size(max = 200, message = "메모는 200자를 넘을 수 없습니다.")
        String memo
) {
}
