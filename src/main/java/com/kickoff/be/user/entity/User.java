package com.kickoff.be.user.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이메일을 주지 않는 제공자(카카오)로 가입하면 null 이다 (계약서 §2, v1.3.2).
     * 유니크 제약은 유지한다 — H2·PostgreSQL 모두 유니크 인덱스에서 null 은 여러 개
     * 허용하므로, 이메일 없는 계정이 늘어도 서로 충돌하지 않는다.
     */
    @Column(unique = true, length = 100)
    private String email;

    /** 소셜로만 가입한 계정은 비밀번호가 없다 (계약서 §3-1). */
    @Column
    private String password;

    @Column(nullable = false, length = 20)
    private String nickname;

    /**
     * 소셜 가입 직후에는 null 이다. 매칭이 성사되면 contact 로 공개되는 값이라
     * 팀을 만들려면 반드시 채워야 한다 (계약서 §4, PHONE_REQUIRED).
     */
    @Column(length = 20)
    private String phone;

    /**
     * 주요 활동 지역 (계약서 §2, v1.6.0). 시/도 단위 문자열이고 nullable —
     * <b>null 이 "전국"</b>이다. 소셜 가입자는 항상 null 로 시작한다.
     *
     * 값 목록은 FE 가 고정 목록으로 고르게 하고 BE 는 길이만 본다. 여기서 열거형으로 굳히면
     * 지역을 하나 늘릴 때마다 배포가 필요해지는데, 그 값은 FE 화면 사정으로 바뀔 값이다.
     */
    @Column(length = 20)
    private String activityRegion;

    /**
     * refresh token 의 SHA-256 해시 (계약서 §3, v1.7.0). 원문은 보관하지 않는다.
     *
     * 사용자당 하나다 — push token 과 같은 단일 기기 정책이라, 새 로그인이 이전 기기의
     * refresh 를 덮어써 무효화한다. 별도 테이블 대신 컬럼으로 둔 것도 그래서다.
     * "1개"라는 규칙이 구조로 보장되고, 여러 기기를 허용하게 되는 날에는 어차피 테이블과
     * 정책을 함께 새로 설계해야 한다.
     */
    @Column(length = 64)
    private String refreshTokenHash;

    /** refresh token 만료 시각. 해시가 맞아도 이 시각이 지났으면 무효다. */
    private OffsetDateTime refreshTokenExpiresAt;

    /**
     * Expo push token. 사용자당 하나이고 마지막 등록이 이긴다 (계약서 §8).
     * 없으면 알림을 조용히 건너뛴다 — 알림이 없다고 매칭 흐름이 막히면 안 된다.
     */
    @Column(length = 200)
    private String expoPushToken;

    /**
     * 약관·개인정보처리방침에 동의한 시각 (계약서 §3, v1.21.0).
     *
     * <b>nullable 이다.</b> v1.21.0 이전에 가입한 계정과 소셜 가입자는 이 값이 없다 —
     * 소셜 가입자의 앱 자체 동의 절차는 v1 범위 밖이다(§9). not null 로 걸면 기존 계정을
     * 어떤 시각으로든 채워 넣어야 하는데, 그건 <b>받지 않은 동의를 받은 것처럼 기록</b>하는
     * 일이다. 동의 기록은 나중에 분쟁의 근거가 되므로 지어내지 않는다.
     *
     * 응답에 싣지 않는다 (계약서 §3). 화면이 쓸 값이 아니고, 서버가 보관하는 기록이다.
     */
    private OffsetDateTime termsAgreedAt;

    @Builder
    private User(String email, String password, String nickname, String phone,
                 String activityRegion, OffsetDateTime termsAgreedAt) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phone = phone;
        this.activityRegion = activityRegion;
        this.termsAgreedAt = termsAgreedAt;
    }

    /**
     * PATCH /api/users/me — 여기서는 {@code null} 이 "안 건드림"이다 (계약서 §3).
     * 활동 지역은 null 이 "지우기"라 규칙이 반대여서 아래 전용 메서드로 뗐다.
     */
    public void updateProfile(String nickname, String phone) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (phone != null) {
            this.phone = phone;
        }
    }

    /** null 이면 활동 지역 없음 = 전국으로 되돌린다 (계약서 §2, v1.6.0). */
    public void updateActivityRegion(String activityRegion) {
        this.activityRegion = activityRegion;
    }

    /**
     * refresh token 을 발급·교체한다 (계약서 §3, v1.7.0).
     * 덮어쓰는 순간 이전 토큰은 무효다 — 로테이션과 단일 기기 정책이 같은 동작이다.
     */
    public void issueRefreshToken(String hash, OffsetDateTime expiresAt) {
        this.refreshTokenHash = hash;
        this.refreshTokenExpiresAt = expiresAt;
    }

    /** 로그아웃. 이미 비어 있어도 그냥 비운다 — 멱등이다 (계약서 §3). */
    /**
     * 비밀번호 교체 (계약서 §3-3, v1.23.0). 호출자가 이미 해시해서 넘긴다 —
     * 엔티티가 인코더를 알면 도메인이 보안 설정에 묶인다.
     */
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void clearRefreshToken() {
        this.refreshTokenHash = null;
        this.refreshTokenExpiresAt = null;
    }

    /**
     * 이 시각 기준으로 refresh token 이 아직 살아 있는지.
     *
     * 해시가 맞는 사용자를 찾아왔더라도 만료는 따로 봐야 한다. 만료된 해시를 지우는 정리
     * 작업이 없어서 행에는 계속 남아 있기 때문이다.
     */
    public boolean hasValidRefreshToken(OffsetDateTime now) {
        return refreshTokenHash != null
                && refreshTokenExpiresAt != null
                && refreshTokenExpiresAt.isAfter(now);
    }

    /** null 을 넣으면 등록 해제다 (계약서 §8). */
    public void updatePushToken(String expoPushToken) {
        this.expoPushToken = expoPushToken;
    }

    public boolean hasPushToken() {
        return expoPushToken != null && !expoPushToken.isBlank();
    }

    public boolean hasPhone() {
        return phone != null && !phone.isBlank();
    }

    /** 소셜로만 가입해 비밀번호가 없는 계정인지. */
    /** 소셜로만 가입한 계정은 이메일이 없을 수 있다 (계약서 §3-1). */
    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }

    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
