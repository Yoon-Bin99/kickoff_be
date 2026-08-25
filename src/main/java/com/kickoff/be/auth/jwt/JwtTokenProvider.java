package com.kickoff.be.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * access token 발급·검증 (계약서 §3). subject 에 userId 를 담는다.
 *
 * v1.7.0 부터 만료가 7일에서 1시간으로 짧아졌다. 짧은 만큼 refresh token 이 로그인을
 * 이어붙인다 — refresh 는 {@link RefreshTokenProvider} 가 맡는다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final Duration expiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") Duration accessExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = accessExpiration;
    }

    public String createToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                // 명시하지 않으면 jjwt 가 키 길이를 보고 HS384/HS512 로 올려버린다
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 유효하면 userId, 아니면 null.
     * 공개 엔드포인트에 만료 토큰이 붙어 와도 요청 자체는 막지 않기 위해 예외 대신 null 을 쓴다.
     */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 토큰: {}", e.getMessage());
            return null;
        }
    }
}
