package com.kickoff.be.verification.service;

/**
 * 이메일 마스킹 (계약서 §3-2, v1.17.0) — {@code kim@example.com → ki***@ex*****.com}.
 *
 * 규칙이 로컬파트와 도메인에서 다르다. 로컬파트는 앞 2자 뒤에 <b>고정 길이</b>
 * {@code ***} 를 붙이고, 도메인 이름은 앞 2자 뒤에 <b>남은 글자 수만큼</b> 별을 붙인다
 * (TLD 는 그대로). 계약서의 예시가 그 차이를 보여준다 — {@code kim}(3자)이 {@code ki***}
 * 가 되는데 길이를 지켰다면 {@code ki*} 였을 것이고, {@code example}(7자)은
 * {@code ex*****} 로 길이를 지켰다.
 *
 * 로컬파트만 고정인 이유는 짐작할 수 있다. 로컬파트 길이는 그 자체로 계정을 좁히는
 * 단서라 감추는 게 낫고, 도메인은 어차피 몇 개 안 되는 공개된 값이라 길이를 지켜도
 * 잃을 게 없다. 대신 화면에서는 길이가 보존된 도메인이 읽기 쉽다.
 */
final class EmailMasker {

    private static final String LOCAL_MASK = "***";
    private static final int KEEP = 2;

    private EmailMasker() {
    }

    static String mask(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            // 이메일 형식이 아니면 통째로 가린다. 여기 올 일은 없지만, 형식을 믿고
            // 쪼개다가 예외를 내는 것보다 낫다 — 이 값은 인증 성공 응답에 실린다.
            return keep(email) + LOCAL_MASK;
        }
        return keep(email.substring(0, at)) + LOCAL_MASK + "@" + maskDomain(email.substring(at + 1));
    }

    /**
     * 도메인은 첫 점을 기준으로 이름과 TLD 를 가른다. {@code example.co.kr} 이면
     * {@code example} 만 가리고 {@code .co.kr} 은 남는다 — 두 번째 점부터는 전부 TLD 쪽이다.
     */
    private static String maskDomain(String domain) {
        int dot = domain.indexOf('.');
        String name = dot < 0 ? domain : domain.substring(0, dot);
        String tld = dot < 0 ? "" : domain.substring(dot);
        String kept = keep(name);
        return kept + "*".repeat(Math.max(0, name.length() - kept.length())) + tld;
    }

    /**
     * 앞 2자. <b>2자 미만</b>이면 첫 자만 (계약서 §3-2).
     *
     * 경계가 "미만"이라 2자짜리는 두 자를 다 남긴다. {@code <=} 로 적으면 2자짜리도
     * 한 자로 깎이는데, 그건 계약과 다르면서 눈에는 잘 안 띈다.
     */
    private static String keep(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.length() < KEEP ? value.substring(0, 1) : value.substring(0, KEEP);
    }
}
