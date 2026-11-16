package com.kickoff.be.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장할 값을 싸고 푸는 부분 (계약서 §5 입금 계좌).
 *
 * 스프링을 띄우지 않는다 — 키 하나만 받는 클래스고, 운영 키가 없을 때 기동이 막히는 것은
 * 컨텍스트를 올리지 않고도 생성자로 볼 수 있다.
 */
class FieldCipherTest {

    private static final String KEY = base64Key("test-only-key-for-account-enc-32");
    private static final String OTHER_KEY = base64Key("another-key-for-account-enc-32bb");

    private final FieldCipher cipher = new FieldCipher(KEY);

    @Test
    @DisplayName("싼 값을 그대로 되돌린다")
    void roundTrips() {
        assertThat(cipher.decrypt(cipher.encrypt("3333-01-1234567"))).isEqualTo("3333-01-1234567");
        assertThat(cipher.decrypt(cipher.encrypt("카카오뱅크"))).isEqualTo("카카오뱅크");
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEmpty();
    }

    @Test
    @DisplayName("null 은 null 그대로다 — 계좌가 없는 글이 대부분이다")
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("암호문에 평문이 남지 않고 버전 접두어가 붙는다")
    void cipherTextHidesPlainTextAndCarriesVersion() {
        String encrypted = cipher.encrypt("3333-01-1234567");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("3333");
        assertThat(encrypted).doesNotContain("1234567");
    }

    /**
     * 같은 IV 로 두 값을 암호화하면 GCM 은 키까지 위험해지고, 그 전에 <b>같은 계좌를 쓰는
     * 글이 서로 드러난다</b>(암호문이 같아진다).
     */
    @Test
    @DisplayName("같은 값을 두 번 싸면 결과가 다르다 — IV 가 매번 새로 뽑힌다")
    void sameInputGivesDifferentCipherText() {
        String first = cipher.encrypt("3333-01-1234567");
        String second = cipher.encrypt("3333-01-1234567");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second));
    }

    /**
     * 암호화만 하는 방식(CBC 등)은 저장된 값이 바뀌어도 알 수 없다. 계좌번호는 조용히
     * 바뀌면 <b>돈이 남에게 가는</b> 값이라 손댄 것을 알아내야 한다.
     */
    @Test
    @DisplayName("한 글자라도 손대면 읽히지 않는다")
    void tamperedValueDoesNotDecrypt() {
        String encrypted = cipher.encrypt("3333-01-1234567");
        String tampered = encrypted.substring(0, encrypted.length() - 2)
                + (encrypted.endsWith("A") ? "B=" : "A=");

        assertThat(cipher.decrypt(tampered)).isNull();
    }

    @Test
    @DisplayName("다른 키로는 읽히지 않는다 — 예외가 아니라 null 이다")
    void otherKeyGivesNull() {
        String encrypted = cipher.encrypt("3333-01-1234567");

        assertThat(new FieldCipher(OTHER_KEY).decrypt(encrypted)).isNull();
    }

    /**
     * 암호화 이전에 저장된 행이 남아 있을 수 있다. 접두어가 없으면 평문으로 보고 그대로
     * 돌려줘야 한다 — 안 그러면 옮기기 전의 글이 전부 "읽을 수 없는 계좌"가 된다.
     */
    @Test
    @DisplayName("접두어가 없는 옛 평문은 그대로 돌려준다")
    void legacyPlainTextPassesThrough() {
        assertThat(cipher.decrypt("3333-01-1234567")).isEqualTo("3333-01-1234567");
        assertThat(FieldCipher.isEncrypted("3333-01-1234567")).isFalse();
        assertThat(FieldCipher.isEncrypted(cipher.encrypt("3333-01-1234567"))).isTrue();
    }

    @Test
    @DisplayName("깨진 base64 는 예외가 아니라 null 이다")
    void brokenBase64GivesNull() {
        assertThat(cipher.decrypt("v1:이건base64가아니다")).isNull();
        assertThat(cipher.decrypt("v1:")).isNull();
    }

    // ── 키 검사 (기동을 막는 자리)

    /**
     * 키 없이 뜨게 해 두면 그 배포는 계좌를 평문으로 쌓거나 저장을 조용히 건너뛴다 —
     * 어느 쪽이든 나중에 알아챈다. 그래서 닫힌 쪽으로 실패한다.
     */
    @Test
    @DisplayName("키가 없으면 만들어지지 않는다")
    void missingKeyIsRefused() {
        assertThatThrownBy(() -> new FieldCipher(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCOUNT_ENC_KEY");
        assertThatThrownBy(() -> new FieldCipher("  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCOUNT_ENC_KEY");
    }

    @Test
    @DisplayName("길이가 32바이트가 아니면 만들어지지 않는다")
    void wrongKeyLengthIsRefused() {
        assertThatThrownBy(() -> new FieldCipher(base64Key("too-short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32바이트");
    }

    /** 오류 문구에 키가 실리면 그 로그가 키 유출 경로가 된다. */
    @Test
    @DisplayName("base64 가 아니면 거부하고, 문구에 값을 싣지 않는다")
    void nonBase64KeyIsRefusedWithoutEchoingIt() {
        String notBase64 = "이건!base64!가!아니다";

        assertThatThrownBy(() -> new FieldCipher(notBase64))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64")
                .hasMessageNotContaining(notBase64);
    }

    private static String base64Key(String raw) {
        return Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
