package com.kickoff.be.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * refresh token 발급과 해싱 (계약서 §3, v1.7.0).
 *
 * access token 과 달리 <b>JWT 가 아니라 불투명한 난수</b>다. JWT 로 만들면 서명만 맞으면
 * 유효해서 로테이션·로그아웃으로 끊을 수가 없다. refresh 는 반드시 끊을 수 있어야 하므로
 * 서버가 상태를 들고 있어야 하고, 그렇다면 토큰 자체에 정보를 담을 이유가 없다.
 *
 * 저장은 원문이 아니라 SHA-256 해시로 한다 (계약서 §3). DB 가 새도 그 값으로는 로그인할 수
 * 없다. 비밀번호처럼 bcrypt 를 쓰지 않는 이유는 이 값이 <b>256비트 난수</b>라서다 — bcrypt 의
 * 느림은 사람이 고른 저엔트로피 비밀번호를 무차별 대입으로부터 지키기 위한 것이고, 여기서는
 * 무차별 대입 자체가 불가능하다. 대신 refresh 는 요청마다 조회되므로 느린 해시를 쓰면
 * 그 비용을 매번 치른다.
 */
@Component
public class RefreshTokenProvider {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Duration expiration;

    public RefreshTokenProvider(@Value("${jwt.refresh-expiration}") Duration refreshExpiration) {
        this.expiration = refreshExpiration;
    }

    /** 새 refresh token 원문. 이 값은 응답으로 나가고 서버에는 남지 않는다. */
    public String issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 저장·조회에 쓰는 해시. 같은 원문이면 항상 같은 값이라 그대로 조회 키가 된다. */
    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JVM 이 반드시 제공한다. 여기 오면 환경이 깨진 것이다.
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }

    public OffsetDateTime expiresAt() {
        return OffsetDateTime.now().plus(expiration);
    }
}
