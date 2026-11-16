package com.kickoff.be.consent.service;

import java.time.OffsetDateTime;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 현행 약관·개인정보처리방침의 시행일 (계약서 §3 「동의」, v1.34.1).
 *
 * <b>이 값 하나가 "누가 다시 동의해야 하는가"를 정한다.</b> 기록이 이 시각보다 이르면
 * 받아야 하는 항목이다 — 그 한 규칙이 소셜 최초 로그인, 구조 개편 전 가입자, 개정 후
 * 재동의를 모두 덮는다.
 *
 * <b>FE 는 이 값을 갖지 않는다</b>(옛 상수 {@code TERMS_EFFECTIVE_AT} 폐지). 양쪽에 두면
 * 문서를 개정하는 날 한쪽만 올라가고, 그러면 동의 화면이 안 뜨거나 영원히 뜬다.
 *
 * <b>문서를 개정하면 이 값을 함께 올린다.</b> 올리지 않으면 개정된 문서가 서빙되는데
 * 아무도 다시 동의하지 않는 상태가 되고, 그건 조용히 틀린다.
 *
 * @param effectiveAt 현행 문서의 시행일. 기본값은 지금 서빙 중인 방침 v2 의 시행일이다.
 */
@ConfigurationProperties(prefix = "kickoff.legal")
public record LegalProperties(OffsetDateTime effectiveAt) {

    /** 지금 {@code /terms}·{@code /privacy} 로 나가는 문서의 시행일 (방침 v2). */
    private static final OffsetDateTime DEFAULT_EFFECTIVE_AT =
            OffsetDateTime.parse("2026-09-08T00:00:00+09:00");

    public LegalProperties {
        // 설정을 빠뜨렸을 때 "아무도 동의가 필요 없다"로 떨어지면 안 된다. 기본값이
        // 없으면 null 비교에서 터지거나(기동 실패) 전원이 통과한다 — 둘 다 나쁘다.
        effectiveAt = effectiveAt == null ? DEFAULT_EFFECTIVE_AT : effectiveAt;
    }
}
