package com.kickoff.be.consent.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동의 한 건 — 누가, 무엇에, 언제 (계약서 §3 「동의」, v1.34.0).
 *
 * <b>그때 시행 중이던 문서의 시행일을 함께 남긴다.</b> "동의했다"만으로는 <b>무엇에</b>
 * 동의했는지 증명할 수 없다 — 약관이 개정되면 같은 시각의 동의가 다른 문서를 가리킨다.
 * 분쟁에서 필요한 것이 그 짝이다.
 *
 * 항목당 한 행이고 다시 동의하면 그 행을 갱신한다. 이력을 쌓지 않는 것은 의도다 —
 * 지금 필요한 것은 "현행 문서에 동의했는가"이고, 그 판정에는 가장 최근 한 건만 쓰인다.
 * 이력이 필요해지면 이 표에 행을 쌓는 쪽으로 바꾸면 된다.
 *
 * 탈퇴하면 함께 지운다 (계약서 §3-4 — 계정에 딸린 기록이다).
 */
@Entity
@Getter
@Table(name = "user_consents", uniqueConstraints = @UniqueConstraint(
        name = "uk_user_consents_user_type", columnNames = {"user_id", "consent_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    /** 서버 시각이다. 클라이언트가 보낸 시각은 근거가 되지 못한다. */
    @Column(name = "agreed_at", nullable = false)
    private OffsetDateTime agreedAt;

    /** 그때 시행 중이던 약관·방침의 시행일 ({@code kickoff.legal.effective-at}). */
    @Column(name = "document_effective_at", nullable = false)
    private OffsetDateTime documentEffectiveAt;

    private UserConsent(User user, ConsentType consentType, OffsetDateTime agreedAt,
                        OffsetDateTime documentEffectiveAt) {
        this.user = user;
        this.consentType = consentType;
        this.agreedAt = agreedAt;
        this.documentEffectiveAt = documentEffectiveAt;
    }

    public static UserConsent of(User user, ConsentType consentType, OffsetDateTime agreedAt,
                                 OffsetDateTime documentEffectiveAt) {
        return new UserConsent(user, consentType, agreedAt, documentEffectiveAt);
    }

    /** 다시 동의했다. 시각과 그때의 시행일을 함께 갱신한다. */
    public void renew(OffsetDateTime agreedAt, OffsetDateTime documentEffectiveAt) {
        this.agreedAt = agreedAt;
        this.documentEffectiveAt = documentEffectiveAt;
    }

    /**
     * 현행 문서에 대한 동의로 볼 수 있는지.
     *
     * 판정은 <b>동의 시각</b>으로 한다 — 계약서 §3 이 "기록이 현행 시행일보다 이르면"
     * 이라고 적은 그대로다. v1.25.0 의 옛 게이트도 같은 기준이었다.
     *
     * <b>아직 오지 않은 시행일은 부르는 쪽에서 걸러진다</b> (v1.35.3 —
     * {@code ConsentService} 의 판정). 그 걸림이 없으면 개정을 미리 공지하는 기간에
     * 아무도 통과하지 못하고, 다시 동의해도 풀리지 않는다.
     */
    public boolean coversDocumentEffectiveAt(OffsetDateTime effectiveAt) {
        return !agreedAt.isBefore(effectiveAt);
    }
}
