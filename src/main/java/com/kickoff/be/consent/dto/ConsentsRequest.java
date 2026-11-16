package com.kickoff.be.consent.dto;

import com.kickoff.be.consent.entity.ConsentType;

/**
 * 동의 화면이 보내는 본문 (계약서 §3 — {@code PUT /api/users/me/consents}, v1.34.0).
 *
 * <b>전부 {@code Boolean} 이다.</b> primitive 로 두면 "안 보냄"과 "false(거부)"가 같은 값이
 * 되는데, 둘은 다루는 법이 다르다 — 안 보낸 것은 "받을 항목이 아니다"일 수 있고 거부는
 * 언제나 400 이다. 어느 쪽인지 알아야 안내 문구를 고를 수 있다.
 *
 * 어떤 항목이 필수인지는 <b>요청이 아니라 서버</b>가 정한다({@code pendingConsents}) —
 * 그래서 여기에는 {@code @NotNull} 이 없고 검증이 서비스에 있다.
 */
public record ConsentsRequest(
        Boolean age14,
        Boolean terms,
        Boolean privacy,
        Boolean contactSharing
) {

    public Boolean valueOf(ConsentType type) {
        return switch (type) {
            case AGE14 -> age14;
            case TERMS -> terms;
            case PRIVACY -> privacy;
            case CONTACT_SHARING -> contactSharing;
        };
    }
}
