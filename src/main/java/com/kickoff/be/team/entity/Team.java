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

    @Builder
    private Team(User owner, String name, String region, String homeGround, SkillLevel skillLevel,
                 AgeGroup ageGroup, int memberCount, String introduction, String logoUrl) {
        this.owner = owner;
        this.name = name;
        this.region = region;
        this.homeGround = homeGround;
        this.skillLevel = skillLevel;
        this.ageGroup = ageGroup;
        this.memberCount = memberCount;
        this.introduction = introduction;
        this.logoUrl = logoUrl;
    }

    /** PATCH 는 전 필드 optional 이라 null 인 항목은 건드리지 않는다. */
    public void update(String name, String region, String homeGround, SkillLevel skillLevel,
                       AgeGroup ageGroup, Integer memberCount, String introduction) {
        if (name != null) {
            this.name = name;
        }
        if (region != null) {
            this.region = region;
        }
        if (homeGround != null) {
            this.homeGround = homeGround;
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
        if (introduction != null) {
            this.introduction = introduction;
        }
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && owner.getId().equals(userId);
    }
}
