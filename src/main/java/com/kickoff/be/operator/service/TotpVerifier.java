package com.kickoff.be.operator.service;

import java.nio.ByteBuffer;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 일회용 비밀번호 검증 (RFC 6238 TOTP — 계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>직접 구현한 이유</b>: 알고리즘이 스무 줄이고(HMAC-SHA1 한 번 + 자르기), 의존성을
 * 하나 더 들이는 쪽이 비용이 크다. 기술 스택은 임의로 늘리지 않는다(CLAUDE.md).
 *
 * 설정은 인증 앱(Google Authenticator 등)의 기본값 그대로다 — SHA-1, 30초, 6자리.
 * 바꾸면 QR 에 파라미터를 실어야 하고, 그 순간 "앱이 기본으로 읽어 주는" 장점이 사라진다.
 *
 * <b>앞뒤 한 칸을 허용한다.</b> 기기 시계가 몇 초 어긋나는 일은 흔하고, 창을 1로 두면
 * 최대 90초가 유효하다 — 그래도 공격자가 쓸 만한 창은 아니다(틀리면 5회에 15분 잠긴다).
 */
@Component
public class TotpVerifier {

    private static final int DIGITS = 6;
    private static final long STEP_SECONDS = 30;
    private static final int WINDOW = 1;
    private static final String ALGORITHM = "HmacSHA1";

    /**
     * 지금 이 비밀키로 유효한 코드인지.
     *
     * @return 맞으면 그 코드가 속한 시간 칸(재사용 거부에 쓴다), 아니면 비어 있음
     */
    public java.util.OptionalLong verify(String base32Secret, String code, Instant now) {
        if (base32Secret == null || base32Secret.isBlank() || code == null) {
            return java.util.OptionalLong.empty();
        }
        String digits = code.trim();
        if (digits.length() != DIGITS || !digits.chars().allMatch(Character::isDigit)) {
            return java.util.OptionalLong.empty();
        }
        byte[] key = decodeBase32(base32Secret);
        if (key.length == 0) {
            return java.util.OptionalLong.empty();
        }
        long step = now.getEpochSecond() / STEP_SECONDS;
        for (long offset = -WINDOW; offset <= WINDOW; offset++) {
            if (constantTimeEquals(digits, generate(key, step + offset))) {
                return java.util.OptionalLong.of(step + offset);
            }
        }
        return java.util.OptionalLong.empty();
    }

    /**
     * 지금 이 비밀키의 코드 — <b>개발·테스트에서 코드를 만들 때</b> 쓴다.
     *
     * 운영 경로는 이 메서드를 부르지 않는다(코드는 인증 앱이 만든다). 로컬에서 운영자
     * 화면을 열어 보려면 dev 비밀키로 여기서 만든 값을 넣으면 된다 — README 참고.
     */
    public String currentCode(String base32Secret, Instant now) {
        return generate(decodeBase32(base32Secret), now.getEpochSecond() / STEP_SECONDS);
    }

    private String generate(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            // RFC 4226 의 동적 잘라내기 — 마지막 바이트 하위 4비트가 시작 위치다.
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return "%06d".formatted(binary % 1_000_000);
        } catch (java.security.GeneralSecurityException e) {
            // HmacSHA1 은 JDK 표준이라 여기 올 일이 없다. 와도 "틀린 코드"로 답한다 —
            // 설정 사고로 운영자 인증이 통과되는 쪽보다 막히는 쪽이 낫다.
            throw new IllegalStateException("TOTP 계산 실패", e);
        }
    }

    /**
     * Base32 (RFC 4648, 대문자·패딩 '='). 인증 앱이 비밀키를 이 형식으로 받는다.
     *
     * 알 수 없는 글자는 무시한다 — 사람이 공백이나 하이픈을 넣어 적어 두는 일이 흔하다.
     */
    private byte[] decodeBase32(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char raw : encoded.toUpperCase(java.util.Locale.ROOT).toCharArray()) {
            int value = alphabet.indexOf(raw);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    /** 코드 비교는 상수 시간으로 — 길이가 6이라 실익은 작지만 비밀값 비교의 기본이다. */
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                b.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
}
