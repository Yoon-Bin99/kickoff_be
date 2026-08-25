package com.kickoff.be.post.dto;

import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

        @PositiveOrZero(message = "대여료는 0 이상이어야 합니다.")
        Integer rentalFee,

        @PositiveOrZero(message = "입금액은 0 이상이어야 합니다.")
        Integer depositAmount,

        @Size(max = 20, message = "은행명은 20자를 넘을 수 없습니다.")
        String bankName,

        @Size(max = 30, message = "계좌번호는 30자를 넘을 수 없습니다.")
        String accountNumber,

        @Size(max = 20, message = "예금주는 20자를 넘을 수 없습니다.")
        String accountHolder,

        PostStatus status,

        /**
         * 지도 좌표 (계약서 §5-1). 좌표 없이도 글은 등록된다 — 장소를 직접 입력한 경우다.
         * 다만 넣는다면 <b>반드시 쌍으로</b>. 하나만 오면 400 이다 (PostService 에서 검증).
         */
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude
) {
}
