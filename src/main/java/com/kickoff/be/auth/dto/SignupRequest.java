package com.kickoff.be.auth.dto;

import com.kickoff.be.consent.dto.SignupConsents;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        /**
         * 비밀번호 규칙 (계약서 §3, v1.16.0) — 8~64자 + 영문·숫자·특수문자 각 1개 이상.
         *
         * 길이와 구성을 <b>따로</b> 검사하는 게 의도다. 하나의 정규식으로 합치면 8자
         * 미만일 때도 "영문·숫자·특수문자를 포함해야 한다"는 안내가 나가서, 사용자는
         * 이미 다 넣었는데 왜 안 되는지 알 수 없다. 두 규칙은 고치는 방법이 다르므로
         * fieldErrors 에도 따로 실려야 한다.
         *
         * <b>가입에만 적용된다.</b> 로그인은 이 규칙을 보지 않는다 — 기존 계정의
         * 비밀번호에 소급하면 규칙을 만든 날 멀쩡한 사용자가 로그인하지 못한다.
         */
        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).*$",
                message = "비밀번호는 영문·숫자·특수문자를 각각 1개 이상 포함해야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
        String nickname,

        @NotBlank(message = "휴대폰 번호는 필수입니다.")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.")
        String phone,

        /**
         * 주요 활동 지역 (계약서 §3, v1.6.0). optional 이다 — 가입 화면이 선택을 권하지만
         * 건너뛸 수 있고, 안 주면 전국이다. 나중에 PATCH 로 채울 수 있다.
         */
        @Size(max = 20, message = "활동 지역은 20자를 넘을 수 없습니다.")
        String activityRegion,

        /**
         * 동의 (계약서 §3 「동의」, v1.34.0·v1.34.1). <b>필수다.</b>
         *
         * 세 항목을 따로 받는다 — 개인정보 보호법 제22조 제1항이 항목을 나누어 받으라고
         * 하고, 무엇에 동의했는지가 기록으로 남아야 한다. 옛 {@code termsAgreed} 한 칸은
         * 폐지했다(보내도 읽지 않는다).
         *
         * 연락처 제공 동의는 여기 없다 — 전화번호가 상대에게 넘어가는 사람은 감독뿐이라
         * <b>팀을 만들 때</b> 받는다(v1.34.1).
         *
         * {@code @NotNull} 로 오브젝트 자체의 누락을 먼저 거른다. 없으면 안쪽 항목 검증이
         * 아예 돌지 않아서, 구버전 앱의 요청이 <b>조용히 동의 없이</b> 통과한다.
         */
        @NotNull(message = "동의 항목이 필요합니다. 앱을 최신 버전으로 업데이트해 주세요.")
        @Valid
        SignupConsents consents,

        /**
         * 전화번호 인증 토큰 (계약서 §3-2, v1.15.0).
         *
         * <b>@NotBlank 를 붙이지 않은 게 의도다.</b> 필수 여부는
         * PHONE_VERIFICATION_REQUIRED 스위치가 정하는데, 여기서 형식 검증으로 막으면
         * 스위치가 꺼져 있어도 400 VALIDATION_FAILED 가 나가 구버전 앱이 깨진다.
         * 계약이 정한 코드는 PHONE_NOT_VERIFIED 이기도 하다.
         */
        String verificationToken
) {
}
