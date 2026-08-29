package com.kickoff.be.verification.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전화번호 인증 한 건 (계약서 §3-2, v1.15.0).
 *
 * <b>발송 한 번이 한 행이다.</b> 확인에 성공하면 같은 행에 토큰이 얹힌다. 행을 나누지
 * 않는 이유는 "이 번호의 최신 발송"만 유효하다는 규칙 때문이다 — 재발송하면 이전 코드가
 * 무효가 되는데, 언제나 마지막 행만 보면 그 규칙이 저절로 지켜진다.
 *
 * 발송 이력이 그대로 남으므로 레이트리밋(1분 1회·1시간 5회)도 이 테이블을 세면 된다.
 * <b>인메모리로 두지 않은 이유가 여기 있다.</b> 재기동마다 리셋되는 제한은 제한이 아니다 —
 * 배포 한 번이면 우회된다.
 *
 * 코드와 토큰은 <b>해시로만</b> 저장한다. 6자리 숫자는 어차피 무차별 대입이 쉽지만,
 * DB 가 새더라도 그 순간 살아 있는 인증을 그대로 가져가지는 못하게 한다. 리프레시 토큰을
 * 해시로 저장하는 것과 같은 결이다.
 */
@Entity
@Getter
@Table(name = "phone_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerification extends BaseTimeEntity {

    /** 연속 실패 허용 횟수 (계약서 §3-2). 넘으면 코드가 무효가 되고 재발송부터 다시다. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "token_hash", length = 100)
    private String tokenHash;

    @Column(name = "token_expires_at")
    private OffsetDateTime tokenExpiresAt;

    @Column(name = "token_used_at")
    private OffsetDateTime tokenUsedAt;

    private PhoneVerification(String phone, String codeHash, OffsetDateTime expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    public static PhoneVerification issue(String phone, String codeHash,
                                          OffsetDateTime expiresAt) {
        return new PhoneVerification(phone, codeHash, expiresAt);
    }

    /** 코드를 더 쓸 수 있는 상태인가. 만료됐거나 5회 틀렸으면 아니다. */
    public boolean isCodeUsable(OffsetDateTime now) {
        return attemptCount < MAX_ATTEMPTS && expiresAt.isAfter(now);
    }

    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    public void issueToken(String tokenHash, OffsetDateTime tokenExpiresAt) {
        this.tokenHash = tokenHash;
        this.tokenExpiresAt = tokenExpiresAt;
        this.tokenUsedAt = null;
    }

    public boolean hasToken() {
        return tokenHash != null;
    }

    public boolean isTokenUsed() {
        return tokenUsedAt != null;
    }

    public boolean isTokenExpired(OffsetDateTime now) {
        return tokenExpiresAt == null || !tokenExpiresAt.isAfter(now);
    }

    /** 1회용이다 (계약서 §3-2). 같은 토큰으로 가입과 번호 변경을 둘 다 할 수 없다. */
    public void useToken(OffsetDateTime now) {
        this.tokenUsedAt = now;
    }
}
