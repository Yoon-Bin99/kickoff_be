package com.kickoff.be.post;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.team.SkillLevel;
import com.kickoff.be.team.Team;
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
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "match_posts", indexes = {
        @Index(name = "idx_match_posts_match_at", columnList = "matchAt"),
        @Index(name = "idx_match_posts_status", columnList = "status")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 글을 쓴 팀. 작성자 판별은 이 팀의 소유자로 한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private OffsetDateTime matchAt;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false, length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FieldType fieldType;

    /** null 이면 상대 실력 무관. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SkillLevel preferredSkillLevel;

    private Integer costPerTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(nullable = false)
    private int viewCount;

    @Builder
    private MatchPost(Team team, String title, String content, OffsetDateTime matchAt, String location,
                      String region, FieldType fieldType, SkillLevel preferredSkillLevel,
                      Integer costPerTeam) {
        this.team = team;
        this.title = title;
        this.content = content;
        this.matchAt = matchAt;
        this.location = location;
        this.region = region;
        this.fieldType = fieldType;
        this.preferredSkillLevel = preferredSkillLevel;
        this.costPerTeam = costPerTeam;
        this.status = PostStatus.OPEN;
        this.viewCount = 0;
    }

    /**
     * PATCH — null 인 항목은 그대로 둔다.
     * preferredSkillLevel/costPerTeam 은 원래 null 이 "무관/미정"이라 이 방식으로는 되돌릴 수 없다.
     */
    public void update(String title, String content, OffsetDateTime matchAt, String location,
                       String region, FieldType fieldType, SkillLevel preferredSkillLevel,
                       Integer costPerTeam, PostStatus status) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (matchAt != null) {
            this.matchAt = matchAt;
        }
        if (location != null) {
            this.location = location;
        }
        if (region != null) {
            this.region = region;
        }
        if (fieldType != null) {
            this.fieldType = fieldType;
        }
        if (preferredSkillLevel != null) {
            this.preferredSkillLevel = preferredSkillLevel;
        }
        if (costPerTeam != null) {
            this.costPerTeam = costPerTeam;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void markMatched() {
        this.status = PostStatus.MATCHED;
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    public boolean isOpen() {
        return this.status == PostStatus.OPEN;
    }

    /** 작성 팀의 소유자인지. */
    public boolean isWrittenBy(Long userId) {
        return team.isOwnedBy(userId);
    }
}
