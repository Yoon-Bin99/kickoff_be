package com.kickoff.be.user.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
     * Expo push token. 사용자당 하나이고 마지막 등록이 이긴다 (계약서 §8).
     * 없으면 알림을 조용히 건너뛴다 — 알림이 없다고 매칭 흐름이 막히면 안 된다.
     */
    @Column(length = 200)
    private String expoPushToken;

    @Builder
    private User(String email, String password, String nickname, String phone,
                 String activityRegion) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.phone = phone;
        this.activityRegion = activityRegion;
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
    public boolean hasPassword() {
        return password != null && !password.isBlank();
    }
}
