package com.kickoff.be.passwordreset.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 비밀번호 재설정 요청 (계약서 §3-3, v1.23.0).
 *
 * <b>이름이 "코드"지만 실제로는 "요청"을 기록한다.</b> 계정이 없거나 소셜 계정이어도
 * 행이 남고, 그때는 {@link #codeHash} 가 null 이다.
 *
 * 그렇게 하는 이유가 이 기능의 핵심이다. 레이트리밋을 "발송된 코드" 기준으로 세면
 * <b>계정 없는 이메일은 영영 한도에 안 걸린다</b> — 429 가 거꾸로 "이 계정은 없다"는
 * 신호가 되어, 계약이 막으려던 존재 노출이 한도 응답으로 새어 나간다. 요청마다 행을
 * 남기면 한도가 계정 유무와 무관해진다.
 *
 * 코드 없는 행은 확인 단계에서 {@code VERIFICATION_EXPIRED} 로 나간다 — 만료·코드 없음·
 * 계정 없음이 전부 같은 응답이라는 계약과 맞는다.
 *
 * 이메일은 <b>소문자로 정규화해</b> 저장한다. 안 그러면 대소문자만 바꿔 한도를 우회한다.
 */
@Entity
@Getter
@Table(name = "password_reset_codes", indexes = {
        @Index(name = "idx_password_reset_codes_email", columnList = "email")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetCode extends BaseTimeEntity {

    /** 연속 실패 허용 횟수 (계약서 §3-3). 넘으면 코드가 무효고 재발송부터 다시다. */
    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    /** null 이면 <b>코드를 발송하지 않은 요청</b>이다 (계정 없음·소셜 계정). */
    @Column(name = "code_hash", length = 100)
    private String codeHash;

    /** 코드가 없으면 만료 시각도 없다. */
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private PasswordResetCode(String email, String codeHash, OffsetDateTime expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    /** 코드를 실어 보낸 요청. 존재하는 이메일 계정일 때만 이쪽이다. */
    public static PasswordResetCode issued(String email, String codeHash,
                                           OffsetDateTime expiresAt) {
        return new PasswordResetCode(email, codeHash, expiresAt);
    }

    /**
     * 코드 없이 <b>기록만</b> 남기는 요청 (계정 없음·소셜 계정).
     * 한도 계산에 들어가라고 남긴다 — 이 행이 없으면 429 가 존재 신호가 된다.
     */
    public static PasswordResetCode recordedOnly(String email) {
        return new PasswordResetCode(email, null, null);
    }

    /** 코드를 더 쓸 수 있는 상태인가. 코드가 아예 없거나, 만료됐거나, 5회 틀렸으면 아니다. */
    public boolean isUsable(OffsetDateTime now) {
        return codeHash != null && expiresAt != null
                && attemptCount < MAX_ATTEMPTS && expiresAt.isAfter(now);
    }

    public void recordFailedAttempt() {
        this.attemptCount++;
    }

    /** 다 쓴 코드는 즉시 무효화한다 — 같은 코드로 두 번 바꿀 수 없다. */
    public void consume() {
        this.codeHash = null;
        this.expiresAt = null;
    }
}
