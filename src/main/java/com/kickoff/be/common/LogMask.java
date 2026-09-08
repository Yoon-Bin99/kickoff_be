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
}
