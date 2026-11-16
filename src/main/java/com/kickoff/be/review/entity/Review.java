package com.kickoff.be.review.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.common.Moderation;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.team.entity.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 리뷰. 평가 단위는 팀이 아니라 <b>매칭(MatchRequest)</b>이다 — 같은 두 팀이 다른 경기로
 * 또 만나면 또 쓸 수 있다 (계약서 §7).
 *
 * (매칭, 작성 팀) 유니크 제약을 둔다. 한 매칭에 양 팀이 한 번씩 쓰므로 행은 최대 둘이고,
 * 서비스의 중복 검사가 동시 요청에 뚫려도 DB 가 막아준다.
 * 수정·삭제는 계약서 §8 에서 v1 범위 밖이라 상태 변경 메서드를 두지 않는다.
 */
@Entity
@Getter
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_reviews_request_reviewer",
                columnNames = {"request_id", "reviewer_team_id"}),
        indexes = @Index(name = "idx_reviews_target_team", columnList = "target_team_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MatchRequest request;

    /** 리뷰를 쓴 팀. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_team_id", nullable = false)
    private Team reviewerTeam;

    /** 평가받는 팀. 요청 본문이 아니라 매칭 관계에서 서버가 결정한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_team_id", nullable = false)
    private Team targetTeam;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String comment;

    /**
     * 운영 조치 (계약서 §7-2, v1.35.0).
     *
     * <b>리뷰는 목록에서 빼지 않고 자리 표시로 남긴다</b> — 별점과 내용만 가리고 행은
     * 그대로 둔다. 조치가 있었다는 사실을 그 자리에 남겨야 하고(정보통신망법 제44조의2
     * 제2항), 통째로 사라지면 받은 쪽이 "내 리뷰가 왜 없어졌지"를 알 길이 없다.
     * 다만 평점·리뷰 수 집계에서는 뺀다.
     */
    @Embedded
    private Moderation moderation = Moderation.none();

    @Builder
    private Review(MatchRequest request, Team reviewerTeam, Team targetTeam, int rating,
                   String comment) {
        this.request = request;
        this.reviewerTeam = reviewerTeam;
        this.targetTeam = targetTeam;
        this.rating = rating;
        this.comment = comment;
    }

    /** 임시 가림 30일 (계약서 §7-2). */
    public void hide(OffsetDateTime now) {
        this.moderation = Moderation.hiddenFrom(now);
    }

    /** 운영자 삭제 (계약서 §7-2). 행은 남는다 — 자리 표시가 남아야 한다. */
    public void removeByOperator() {
        this.moderation = Moderation.removed();
    }

    public void clearModeration() {
        this.moderation = Moderation.none();
    }

    public boolean isModeratedAt(OffsetDateTime now) {
        return moderation != null && moderation.isActiveAt(now);
    }
}
