package com.kickoff.be.post.dto;

import com.kickoff.be.common.PatchableDouble;
import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.team.entity.SkillLevel;
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
         * 지도 좌표 (계약서 §5-1). 여기만 PatchableDouble 인 이유는 <b>지우기</b> 때문이다 —
         * 평범한 Double 로 받으면 "안 보냄"과 "null 을 보냄"이 구분되지 않아, 좌표를 지우려는
         * 요청이 조용히 무시된다. 범위 검증은 어노테이션이 아니라 PostService 가 한다.
         */
        PatchableDouble latitude,

        PatchableDouble longitude
) {
}
