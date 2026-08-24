package com.kickoff.be.oauth.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 계정 연동 이력. User 에 provider 컬럼을 붙이지 않고 별도 엔티티로 둔 이유는,
 * 한 사용자가 카카오·네이버를 모두 연동할 수 있고 계약서의 authProviders 가 배열이기 때문이다.
 *
 * (provider, providerUserId) 유니크 — 같은 소셜 계정이 두 사용자에게 붙으면 로그인할 때마다
 * 다른 계정으로 들어가게 된다. 연동 해제는 계약서 §8 에서 범위 밖이라 삭제 메서드를 두지 않는다.
 */
@Entity
@Getter
@Table(name = "social_accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_social_accounts_provider_user",
                columnNames = {"provider", "provider_user_id"}),
        indexes = @Index(name = "idx_social_accounts_user", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 제공자가 준 고유 식별자. 이메일과 달리 바뀌지 않아 연동의 기준이 된다. */
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    /** 연동 시점의 제공자 이메일. 기록용이라 이후 계정 판별에는 쓰지 않는다. */
    @Column(length = 100)
    private String email;

    @Builder
    private SocialAccount(User user, AuthProvider provider, String providerUserId, String email) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
    }
}
