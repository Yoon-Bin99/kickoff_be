package com.kickoff.be.post.dto;

import com.kickoff.be.common.Patchable;
import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * PATCH — POST 와 같은 필드 + status, 전부 optional (계약서 §5).
 *
 * 모든 필드가 {@link Patchable} 인 이유는 <b>"안 보냄"과 "명시적 null"을 구분</b>해야 하기
 * 때문이다 (계약서 §5 지우기 규칙, v1.5.1). 평범한 타입으로 받으면 둘 다 null 이라
 * 지우려는 요청이 조용히 무시되고, 반대로 필수 필드에 null 을 보내도 400 을 낼 수 없다.
 *
 * 지울 수 있는지 없는지는 타입이 아니라 계약이 정한다 — PostService 가 판단한다.
 * 어노테이션은 {@code PatchableValueExtractor} 덕분에 담긴 값에 그대로 걸린다.
 */
public record PostUpdateRequest(

        // ── 지울 수 없는 필드. 명시적 null 은 400 이다 (계약서 §5)

        @Size(min = 2, max = 60, message = "제목은 2~60자여야 합니다.")
        Patchable<String> title,

        @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
        Patchable<String> content,

        @Future(message = "경기 일시는 미래여야 합니다.")
        Patchable<OffsetDateTime> matchAt,

        @Size(min = 1, max = 100, message = "장소는 1~100자여야 합니다.")
        Patchable<String> location,

        @Size(min = 1, max = 50, message = "지역은 1~50자여야 합니다.")
        Patchable<String> region,

        Patchable<FieldType> fieldType,

        Patchable<PostStatus> status,

        // ── 지울 수 있는 필드 (계약서 §5, v1.5.1)

        /** null 이면 "실력 무관"으로 되돌린다. */
        Patchable<SkillLevel> preferredSkillLevel,

        /** null 이면 "대여료 미정"으로 되돌린다. */
        @PositiveOrZero(message = "대여료는 0 이상이어야 합니다.")
        Patchable<Integer> rentalFee,

        /**
         * null 이면 입금액과 <b>계좌 3필드가 함께</b> 지워진다. 금액 없는 계좌는 "무료 경기인데
         * 입금 안내가 붙은" 어긋남을 만들기 때문이다. 계좌만 따로 지우는 것은 400 이다.
         */
        @PositiveOrZero(message = "입금액은 0 이상이어야 합니다.")
        Patchable<Integer> depositAmount,

        /**
         * 계좌 3필드는 개별로 지울 수 없다 (계약서 §5). 빈 문자열도 지우기로 쳐주지 않고
         * 길이 규칙으로 거절한다 — {@code ""} 를 null 처럼 받아주면 "은행명이 빈 칸인 계좌"가
         * 저장돼 지운 것도 안 지운 것도 아닌 상태가 된다.
         */
        @Size(min = 1, max = 20, message = "은행명은 1~20자여야 합니다.")
        Patchable<String> bankName,

        @Size(min = 1, max = 30, message = "계좌번호는 1~30자여야 합니다.")
        Patchable<String> accountNumber,

        @Size(min = 1, max = 20, message = "예금주는 1~20자여야 합니다.")
        Patchable<String> accountHolder,

        /**
         * 지도 좌표 (계약서 §5-1). 쌍으로만 바뀐다 — 한쪽만 보내면 400 이고, 지우려면 둘 다
         * null 이다. 범위 검증은 어노테이션이 아니라 PostService 가 한다.
         */
        Patchable<Double> latitude,

        Patchable<Double> longitude
) {
}
