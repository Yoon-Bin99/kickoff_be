package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** PATCH — 전 필드 optional. null 인 항목은 기존 값을 유지한다 (계약서 §4). */
public record TeamUpdateRequest(

        @Size(min = 2, max = 30, message = "팀 이름은 2~30자여야 합니다.")
        String name,

        @Size(min = 1, max = 50, message = "지역은 1~50자여야 합니다.")
        String region,

        @Size(max = 100, message = "홈 구장은 100자를 넘을 수 없습니다.")
        String homeGround,

        SkillLevel skillLevel,

        AgeGroup ageGroup,

        @Min(value = 1, message = "팀 인원은 1명 이상이어야 합니다.")
        @Max(value = 100, message = "팀 인원은 100명을 넘을 수 없습니다.")
        Integer memberCount,

        @Size(max = 1000, message = "소개는 1000자를 넘을 수 없습니다.")
        String introduction
) {
}
