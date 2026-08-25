package com.kickoff.be.team.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * POST /api/teams/{teamId}/records (계약서 §4-1, v1.8.0).
 *
 * 수정이 없으므로 이 DTO 하나뿐이다 — 잘못 넣었으면 지우고 다시 넣는다.
 */
public record TeamRecordCreateRequest(

        @NotNull(message = "경기 날짜는 필수입니다.")
        @PastOrPresent(message = "경기 날짜는 오늘 이전이어야 합니다.")
        LocalDate playedOn,

        @NotBlank(message = "상대 팀 이름은 필수입니다.")
        @Size(min = 1, max = 30, message = "상대 팀 이름은 1~30자여야 합니다.")
        String opponentName,

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
