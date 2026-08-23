package com.kickoff.be.post.dto;

import com.kickoff.be.post.FieldType;
import com.kickoff.be.team.SkillLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record PostCreateRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(min = 2, max = 60, message = "제목은 2~60자여야 합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
        String content,

        @NotNull(message = "경기 일시는 필수입니다.")
        @Future(message = "경기 일시는 미래여야 합니다.")
        OffsetDateTime matchAt,

        @NotBlank(message = "장소는 필수입니다.")
        @Size(max = 100, message = "장소는 100자를 넘을 수 없습니다.")
        String location,

        @NotBlank(message = "지역은 필수입니다.")
        @Size(max = 50, message = "지역은 50자를 넘을 수 없습니다.")
        String region,

        @NotNull(message = "구장 유형은 필수입니다.")
        FieldType fieldType,

        /** null 이면 상대 실력 무관. */
        SkillLevel preferredSkillLevel,

        @PositiveOrZero(message = "팀당 비용은 0 이상이어야 합니다.")
        Integer costPerTeam
) {
}
