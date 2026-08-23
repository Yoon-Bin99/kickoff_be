package com.kickoff.be.post.dto;

import com.kickoff.be.post.FieldType;
import com.kickoff.be.post.PostStatus;
import com.kickoff.be.team.SkillLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** PATCH — POST 와 같은 필드 + status, 전부 optional (계약서 §5). */
public record PostUpdateRequest(

        @Size(min = 2, max = 60, message = "제목은 2~60자여야 합니다.")
        String title,

        @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
        String content,

        @Future(message = "경기 일시는 미래여야 합니다.")
        OffsetDateTime matchAt,

        @Size(min = 1, max = 100, message = "장소는 1~100자여야 합니다.")
        String location,

        @Size(min = 1, max = 50, message = "지역은 1~50자여야 합니다.")
        String region,

        FieldType fieldType,

        SkillLevel preferredSkillLevel,

        @PositiveOrZero(message = "팀당 비용은 0 이상이어야 합니다.")
        Integer costPerTeam,

        PostStatus status
) {
}
