package com.kickoff.be.team.dto;

import com.kickoff.be.common.Patchable;
import com.kickoff.be.team.entity.AgeGroup;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH — 전 필드 optional (계약서 §4).
 *
 * 필드를 안 보내면 기존 값이 유지되고, 명시적 {@code null} 은 지우기다 (v1.5.1). 둘을
 * 구분하려고 {@link Patchable} 로 받는다 — 자세한 이유는 그 클래스에 적어 뒀다.
 * 지울 수 있는 건 홈 구장과 소개뿐이고, 나머지에 null 을 보내면 400 이다.
 */
public record TeamUpdateRequest(

        // ── 지울 수 없는 필드

        @Size(min = 2, max = 30, message = "팀 이름은 2~30자여야 합니다.")
        Patchable<String> name,

        @Size(min = 1, max = 50, message = "지역은 1~50자여야 합니다.")
        Patchable<String> region,

        Patchable<SkillLevel> skillLevel,

        Patchable<AgeGroup> ageGroup,

        @Min(value = 1, message = "팀 인원은 1명 이상이어야 합니다.")
        @Max(value = 100, message = "팀 인원은 100명을 넘을 수 없습니다.")
        Patchable<Integer> memberCount,

        // ── 지울 수 있는 필드 (계약서 §4, v1.5.1)

        /** null 이면 홈 구장 없음으로 되돌린다. */
        @Size(min = 1, max = 100, message = "홈 구장은 1~100자여야 합니다.")
        Patchable<String> homeGround,

        /** null 이면 소개 없음으로 되돌린다. */
        @Size(min = 1, max = 1000, message = "소개는 1~1000자여야 합니다.")
        Patchable<String> introduction,

        // ── 팀 프로필 확장 (계약서 §4-1, v1.8.0). 전부 지울 수 있다.

        @Min(value = 1900, message = "창단 연도는 1900년 이후여야 합니다.")
        Patchable<Integer> foundedYear,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "팀 색은 #RRGGBB 형식이어야 합니다.")
        Patchable<String> teamColor,

        @Size(min = 1, max = 10, message = "포메이션은 1~10자여야 합니다.")
        Patchable<String> formation
) {
}
