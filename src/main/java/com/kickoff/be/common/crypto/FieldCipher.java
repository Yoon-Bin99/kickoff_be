package com.kickoff.be.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 저장할 값 하나를 AES-256-GCM 으로 싸고 푼다.
 *
 * 쓰는 곳은 모집글의 입금 계좌 세 칸이다 (계약서 §5). 복호화 키 없이는 DB 덤프만으로
 * 계좌를 읽을 수 없게 하는 것이 목적이다.
 *
 * <b>GCM 을 쓰는 이유.</b> 암호화만 하는 방식(CBC 등)은 저장된 값이 바뀌어도 알 수 없다 —
 * GCM 은 인증 태그가 함께 있어 한 바이트라도 손대면 복호화가 실패한다. 계좌번호는 조용히
 * 바뀌면 <b>돈이 남에게 가는</b> 값이라 그 성질이 중요하다.
 *
 * <b>IV 는 값마다 새로 뽑는다.</b> 같은 IV 로 두 값을 암호화하면 GCM 은 키까지 위험해지고,
 * 그 전에 같은 평문이 같은 암호문이 되어 "같은 계좌를 쓰는 글"이 드러난다.
 *
 * <b>저장 형식</b>: {@code v1:} + base64(IV ‖ 암호문 ‖ 태그). 버전 접두어가 있어야 키나
 * 알고리즘을 바꿀 때 옛 값을 알아보고 읽을 수 있다 — 접두어가 없으면 그날 전체를 한
 * 트랜잭션에 바꿔야 한다.
 *
 * 키·평문·암호문은 어디에도 로그로 남기지 않는다.
 */
@Component
public class FieldCipher {

    /** 현재 형식. 읽을 때 이 접두어가 없으면 암호화되지 않은 옛 값으로 본다. */
    public static final String PREFIX = "v1:";

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /**
     * 키가 없거나 길이가 틀리면 <b>기동을 거부한다.</b>
     *
     * 닫힌 쪽으로 실패해야 하는 자리다. 키 없이도 뜨게 해 두면 그 배포는 계좌를 평문으로
     * 쌓거나 저장 자체를 조용히 건너뛴다 — 어느 쪽이든 나중에 알아챈다. 개발·테스트
     * 프로파일에는 기본값이 있으므로({@code application.yaml}) 여기서 걸리는 것은
     * <b>운영에서 값을 빠뜨린 경우</b>뿐이다.
     *
     * 오류 문구에 키를 싣지 않는다. 길이만 적는다 — 그것만으로 무엇이 틀렸는지 충분하다.
     */
    public FieldCipher(@Value("${kickoff.account.encryption-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("""
                    ACCOUNT_ENC_KEY 가 없습니다 — 모집글의 입금 계좌를 암호화할 수 없어 기동하지 않습니다.
                    32바이트 난수를 base64 로 인코딩해 넣으세요.""");
        }
        byte[] decoded = decodeKey(base64Key.trim());
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "ACCOUNT_ENC_KEY 의 길이가 %d바이트입니다 — AES-256 은 %d바이트를 요구합니다."
                            .formatted(decoded.length, KEY_BYTES));
        }
        this.key = new SecretKeySpec(decoded, ALGORITHM);
    }

    private static byte[] decodeKey(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            // 메시지에 값을 싣지 않는다.
            throw new IllegalStateException("ACCOUNT_ENC_KEY 가 base64 가 아닙니다.");
        }
    }

    /** @return {@code v1:} + base64(IV ‖ 암호문 ‖ 태그). null 은 그대로 null 이다. */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] packed = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(cipherText, 0, packed, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException e) {
            // 여기 오면 JVM 의 암호 제공자가 깨진 것이다. 저장을 포기하는 쪽이 맞다 —
            // 평문으로 흘려 넣는 것보다 실패가 낫다.
            throw new IllegalStateException("계좌 암호화에 실패했습니다.", e);
        }
    }

    /**
     * @return 평문. <b>복호화에 실패하면 null</b> — 키가 바뀌었거나 값이 손상된 경우다.
     *         예외를 던지지 않는 이유: 이 호출은 글을 읽는 길목(JPA 변환기)에 있어서,
     *         여기서 터지면 계좌와 무관한 <b>목록 조회까지 500</b> 이 된다. 무엇을 할지는
     *         값을 쓰는 쪽이 정한다(계좌를 못 내려주고 그 글의 id 를 ERROR 로 남긴다).
     */
    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (packed.length <= IV_BYTES) {
                return null;
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 로그를 남기지 않는다 — 여기는 글 하나를 읽을 때마다 지나는 자리라, 목록
            // 조회 한 번이 수십 줄을 만든다. 그리고 이 자리에는 postId 가 없어서 남겨도
            // 추적에 쓸 수 없다. 쓰는 쪽(PostService)이 postId 와 함께 ERROR 를 남긴다.
            return null;
        }
    }

    /** 이미 이 형식으로 저장된 값인지. 옛 평문 행을 옮길 때 건너뛸 기준이다. */
    public static boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }
}
