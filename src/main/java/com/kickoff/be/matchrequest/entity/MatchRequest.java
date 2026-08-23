package com.kickoff.be.matchrequest.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.team.entity.Team;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매칭 신청. (글, 신청팀) 유니크 제약은 두지 않는다 —
 * 계약서상 CANCELED/REJECTED 이력이 있으면 재신청이 허용되기 때문.
 */
@Entity
@Getter
@Table(name = "match_requests", indexes = {
        @Index(name = "idx_match_requests_post", columnList = "post_id"),
        @Index(name = "idx_match_requests_applicant", columnList = "applicant_team_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private MatchPost post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_team_id", nullable = false)
    private Team applicantTeam;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;

    /** 글 작성자가 상대 팀의 입금을 확인했는지. 기록용 플래그라 글/신청 상태는 건드리지 않는다. */
    @Column(nullable = false)
    private boolean depositPaid;

    @Builder
    private MatchRequest(MatchPost post, Team applicantTeam, String message) {
        this.post = post;
        this.applicantTeam = applicantTeam;
        this.message = message;
        this.status = RequestStatus.PENDING;
        this.depositPaid = false;
    }

    public boolean isPending() {
        return this.status == RequestStatus.PENDING;
    }

    public boolean isAccepted() {
        return this.status == RequestStatus.ACCEPTED;
    }

    public void confirmDeposit() {
        this.depositPaid = true;
    }

    public void accept() {
        this.status = RequestStatus.ACCEPTED;
    }

    public void reject() {
        this.status = RequestStatus.REJECTED;
    }

    public void cancel() {
        this.status = RequestStatus.CANCELED;
    }
}
