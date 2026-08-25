package com.kickoff.be.team.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "teams")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Team extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 한 사용자는 팀을 하나만 소유한다 (계약서 §4). */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(length = 100)
    private String homeGround;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillLevel skillLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgeGroup ageGroup;

    @Column(nullable = false)
    private int memberCount;

    @Column(length = 1000)
    private String introduction;

    /** v1 에는 업로드가 없어서 항상 null. 계약서 필드 유지용. */
    @Column(length = 500)
    private String logoUrl;

    /** 창단 연도 (계약서 §4-1, v1.8.0). nullable — 모르는 팀이 많다. */
    private Integer foundedYear;

    /** 팀 색 {@code #RRGGBB} (계약서 §4-1). 형식 검증은 DTO 가 한다. */
    @Column(length = 7)
    private String teamColor;

    /**
     * 주 포메이션 (계약서 §4-1). "4-4-2" 같은 자유 문자열이다.
     * 열거형으로 굳히지 않은 건 팀마다 쓰는 표기가 다르고 새 조합이 계속 나오기 때문이다.
     */
    @Column(length = 10)
    private String formation;

    @Builder
    private Team(User owner, String name, String region, String homeGround, SkillLevel skillLevel,
                 AgeGroup ageGroup, int memberCount, String introduction, String logoUrl,
                 Integer foundedYear, String teamColor, String formation) {
        this.owner = owner;
        this.name = name;
        this.region = region;
        this.homeGround = homeGround;
        this.skillLevel = skillLevel;
        this.ageGroup = ageGroup;
        this.memberCount = memberCount;
        this.introduction = introduction;
        this.logoUrl = logoUrl;
        this.foundedYear = foundedYear;
        this.teamColor = teamColor;
        this.formation = formation;
    }

    /**
     * 지울 수 없는 필드만 여기서 바꾼다 — 여기서는 {@code null} 이 "안 건드림"이다.
     * 홈 구장·소개는 null 이 "지우기"라 규칙이 반대여서 아래 전용 메서드로 뗐다.
     */
    public void update(String name, String region, SkillLevel skillLevel,
                       AgeGroup ageGroup, Integer memberCount) {
        if (name != null) {
            this.name = name;
        }
        if (region != null) {
            this.region = region;
        }
        if (skillLevel != null) {
            this.skillLevel = skillLevel;
        }
        if (ageGroup != null) {
            this.ageGroup = ageGroup;
        }
        if (memberCount != null) {
            this.memberCount = memberCount;
        }
    }

    /** null 이면 홈 구장 없음으로 되돌린다 (계약서 §4, v1.5.1). */
    public void updateHomeGround(String homeGround) {
        this.homeGround = homeGround;
    }

    /** null 이면 소개 없음으로 되돌린다 (계약서 §4, v1.5.1). */
    public void updateIntroduction(String introduction) {
        this.introduction = introduction;
    }

    /** null 이면 창단 연도 없음으로 되돌린다 (계약서 §4-1, v1.8.0). */
    public void updateFoundedYear(Integer foundedYear) {
        this.foundedYear = foundedYear;
    }

    /** null 이면 팀 색 없음으로 되돌린다 (계약서 §4-1, v1.8.0). */
    public void updateTeamColor(String teamColor) {
        this.teamColor = teamColor;
    }

    /** null 이면 포메이션 없음으로 되돌린다 (계약서 §4-1, v1.8.0). */
    public void updateFormation(String formation) {
        this.formation = formation;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && owner.getId().equals(userId);
    }
}
