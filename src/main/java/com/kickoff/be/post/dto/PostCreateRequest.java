package com.kickoff.be.post.dto;

import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

        /** 작성 팀이 이미 낸 총 구장 대여료. */
        @PositiveOrZero(message = "대여료는 0 이상이어야 합니다.")
        Integer rentalFee,

        /** 상대 팀이 보낼 금액. 이 값을 넣으면 계좌 세 필드가 전부 필수다. */
        @PositiveOrZero(message = "입금액은 0 이상이어야 합니다.")
        Integer depositAmount,

        @Size(max = 20, message = "은행명은 20자를 넘을 수 없습니다.")
        String bankName,

        @Size(max = 30, message = "계좌번호는 30자를 넘을 수 없습니다.")
        String accountNumber,

        @Size(max = 20, message = "예금주는 20자를 넘을 수 없습니다.")
        String accountHolder,

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
