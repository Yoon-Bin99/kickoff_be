package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
        String introduction,

        // ── 팀 프로필 확장 (계약서 §4-1, v1.8.0). 전부 optional.

        /**
         * 창단 연도. 상한을 어노테이션 상수로 못 박을 수 없어 서비스에서 "현재 연도 이하"를
         * 본다 — 해가 바뀌면 상한도 같이 올라가야 한다.
         */
        @Min(value = 1900, message = "창단 연도는 1900년 이후여야 합니다.")
        Integer foundedYear,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "팀 색은 #RRGGBB 형식이어야 합니다.")
        String teamColor,

        @Size(max = 10, message = "포메이션은 10자를 넘을 수 없습니다.")
        String formation
) {
}
