package com.kickoff.be.consent.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 가입 요청의 {@code consents} (계약서 §3 signup, v1.34.1).
 *
 * 세 항목이 모두 {@code true} 여야 한다. 연락처 제공 동의는 여기 없다 — 팀을 만들 때
 * 받는다(v1.34.1).
 *
 * <b>{@code Boolean} 에 {@code @NotNull} 을 따로 붙인다.</b> Bean Validation 의
 * {@code @AssertTrue} 는 null 을 통과시키고, primitive 로 두면 필드를 아예 안 보낸 요청이
 * false 로 바인딩돼 <b>누락과 거부가 구별되지 않는다.</b> 누락은 구버전 앱이 보내는 모양이라
 * 안내가 "업데이트해 주세요"여야 하고, 거부는 "동의해야 가입할 수 있습니다"다. 둘 다
 * 400 이지만 사용자가 할 일이 다르다. (옛 {@code termsAgreed} 에서 가져온 판단이다.)
 */
public record SignupConsents(

        @NotNull(message = "만 14세 이상 여부가 필요합니다. 앱을 최신 버전으로 업데이트해 주세요.")
        @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
        Boolean age14,

        @NotNull(message = "이용약관 동의 여부가 필요합니다. 앱을 최신 버전으로 업데이트해 주세요.")
        @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
        Boolean terms,

        @NotNull(message = "개인정보 수집·이용 안내 확인 여부가 필요합니다. 앱을 최신 버전으로 업데이트해 주세요.")
        @AssertTrue(message = "개인정보 수집·이용 안내를 확인해야 가입할 수 있습니다.")
        Boolean privacy
) {
}
