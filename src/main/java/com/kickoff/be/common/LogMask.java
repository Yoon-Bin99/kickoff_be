package com.kickoff.be.common;

/**
 * 로그에 남길 값에서 개인정보를 가린다.
 *
 * <b>왜 필요한가.</b> 메일·문자 발송 경로는 성패를 남겨야 운영에서 원인을 찾을 수 있는데,
 * 그 값이 곧 전화번호와 이메일이다. 그대로 찍으면 로그 저장소가 개인정보 보관 장소가 된다 —
 * 우리 방침(§7 파기)은 그 사본까지 책임지지 않고, 로그는 지우기도 어렵다. 어느 사용자였는지
 * 알아볼 정도만 남기고 나머지를 가린다.
 *
 * <b>{@code EmailMasker} 와 왜 따로 두는가.</b> 그쪽은 계약서 §3-2 가 정한 <b>응답 형식</b>이다.
 * 화면에 보이는 값이라 규칙을 바꾸면 계약이 바뀐다. 여기는 로그 전용이라 계약과 무관하게
 * 더 세게 가려도 된다. 한 클래스로 합치면 "로그를 더 가리자"는 변경이 API 응답을 바꾸게 된다.
 */
public final class LogMask {

    private static final String UNKNOWN = "?";

    private LogMask() {
    }

    /** {@code kim@example.com → k***@example.com}. 도메인은 남긴다 — 발송 실패는 도메인 단위로 갈린다. */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return UNKNOWN;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0) {
            // 형식이 아니면 통째로 가린다. 값이 무엇이든 로그에 원문을 남기지 않는다.
            return UNKNOWN;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** {@code 010-1234-5678 → ***-****-5678}. 뒤 4자리만 남긴다 — 문의가 오면 그걸로 대조한다. */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 4) {
            return UNKNOWN;
        }
        return "***-****-" + phone.substring(phone.length() - 4);
    }

    /**
     * 임의의 문자열 안에서 <b>휴대폰 번호처럼 생긴 부분만</b> 가린다. 나머지는 그대로 둔다.
     *
     * 외부 제공자의 오류 응답을 로그에 남길 때 쓴다. 응답 전체를 버리면 실패 사유
     * (잔액 부족인지 서명 불일치인지)를 잃고, 그대로 남기면 제공자가 본문에 실어 보낸
     * 수신번호를 우리 로그에 심게 된다. 번호만 도려내면 둘 다 지킨다.
     *
     * 하이픈이 있든 없든 잡고, 앞뒤 4자리 규칙은 {@link #phone} 과 같게 맞춘다.
     */
    public static String phonesIn(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return PHONE_LIKE.matcher(text)
                .replaceAll(match -> "***-****-" + match.group(3));
    }

    /**
     * {@code 010-1234-5678} 과 {@code 01012345678} 을 모두 잡는다. 국번은 3~4자리라
     * 두 형태를 한 패턴으로 둔다. 다른 나라 번호나 일반 숫자열은 대상이 아니다 —
     * 우리가 다루는 값이 국내 휴대폰 번호뿐이라 좁게 잡는 편이 오탐이 적다.
     */
    private static final java.util.regex.Pattern PHONE_LIKE =
            java.util.regex.Pattern.compile("(01[016789])-?(\\d{3,4})-?(\\d{4})");
}
