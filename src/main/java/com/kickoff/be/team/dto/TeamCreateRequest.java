package com.kickoff.be.team.dto;

import com.kickoff.be.team.AgeGroup;
import com.kickoff.be.team.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TeamCreateRequest(

        @NotBlank(message = "팀 이름은 필수입니다.")
        @Size(min = 2, max = 30, message = "팀 이름은 2~30자여야 합니다.")
        String name,

        @NotBlank(message = "지역은 필수입니다.")
        @Size(max = 50, message = "지역은 50자를 넘을 수 없습니다.")
        String region,

        @Size(max = 100, message = "홈 구장은 100자를 넘을 수 없습니다.")
        String homeGround,

        @NotNull(message = "실력 수준은 필수입니다.")
        SkillLevel skillLevel,

        @NotNull(message = "연령대는 필수입니다.")
        AgeGroup ageGroup,

        @NotNull(message = "팀 인원은 필수입니다.")
        @Min(value = 1, message = "팀 인원은 1명 이상이어야 합니다.")
        @Max(value = 100, message = "팀 인원은 100명을 넘을 수 없습니다.")
        Integer memberCount,

        @Size(max = 1000, message = "소개는 1000자를 넘을 수 없습니다.")
        String introduction
) {
}
