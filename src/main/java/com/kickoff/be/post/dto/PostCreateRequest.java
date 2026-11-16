package com.kickoff.be.post.dto;

import com.kickoff.be.team.entity.SkillLevel;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

        /**
         * 경기장 주소 (계약서 §5, v1.29.0). optional — 장소 검색(§5-1)에서 고르면 FE 가
         * 채워 보내고, 장소명을 직접 입력하면 비어 온다.
         *
         * <b>검증은 길이만 한다.</b> 사용자가 고친 값을 그대로 저장하라는 것이 계약서
         * 문면이고, 실제 주소와 맞는지 확인할 방법도 없다.
         */
        @Size(max = 100, message = "주소는 100자를 넘을 수 없습니다.")
        String locationAddress,

        @NotBlank(message = "지역은 필수입니다.")
        @Size(max = 50, message = "지역은 50자를 넘을 수 없습니다.")
        String region,

        /** null 이면 상대 실력 무관. */
        SkillLevel preferredSkillLevel,

        /** 작성 팀이 이미 낸 총 구장 대여료. */
        @PositiveOrZero(message = "대여료는 0 이상이어야 합니다.")
        Integer rentalFee,

        /** 상대 팀이 보낼 금액. 이 값을 넣으면 계좌 세 필드가 전부 필수다. */
        @PositiveOrZero(message = "입금액은 0 이상이어야 합니다.")
        Integer depositAmount,

        /**
         * 계좌 3필드는 빈 문자열을 받지 않는다 (계약서 §5). {@code ""} 를 허용하면 "은행명이
         * 빈 칸인 계좌"가 저장되는데, PATCH 에서도 빈 문자열은 거절되므로 <b>나중에 고칠 수
         * 없는 글</b>이 된다. 값을 넣지 않으려면 필드를 빼거나 null 로 보낼 것.
         */
        @Size(min = 1, max = 20, message = "은행명은 1~20자여야 합니다.")
        String bankName,

        @Size(min = 1, max = 30, message = "계좌번호는 1~30자여야 합니다.")
        String accountNumber,

        @Size(min = 1, max = 20, message = "예금주는 1~20자여야 합니다.")
        String accountHolder,

        /**
         * 사용자가 고른 장소의 카카오 장소 ID (계약서 §5-1, v1.37.0).
         *
         * 없이도 글은 등록된다 — 장소를 직접 입력한 경우다. 다만 넣는다면
         * <b>{@code placeQuery} 와 쌍으로</b>. 하나만 오면 400 이다 (PostService 에서 검증).
         *
         * 서버는 이 값을 카카오에 확인하지 않는다 — ID 로 조회하는 API 가 없다. 형식만 본다.
         */
        @Pattern(regexp = "[0-9]{1,20}", message = "장소 ID 는 숫자 1~20자여야 합니다.")
        String kakaoPlaceId,

        /** 그 장소를 찾을 때 사용자가 친 검색어 (계약서 §5-1). 사용자의 글자다. */
        @Size(min = 1, max = 100, message = "검색어는 1~100자여야 합니다.")
        String placeQuery,

        /**
         * 계좌 공개 확인 (계약서 §5, v1.34.0).
         *
         * 요청 본문에 계좌 3필드 중 하나라도 값이 있으면 <b>true 필수</b>다 — "이 계좌
         * 정보는 매칭이 수락된 상대 팀 감독에게 보인다"는 확인이다. 판정은 PostService 가
         * 한다: 어노테이션으로는 "다른 필드가 있을 때만 필수"를 표현할 수 없다.
         *
         * <b>요청 본문 기준</b>이라 계좌를 건드리지 않는 PATCH 에는 필요 없다. 글에
         * 이미 계좌가 있는지와 무관하다 — 묻는 것은 "지금 이 값을 적겠는가"다.
         */
        Boolean paymentShareConfirmed
) {
}
