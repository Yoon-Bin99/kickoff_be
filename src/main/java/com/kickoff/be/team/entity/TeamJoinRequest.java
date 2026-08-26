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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 가입 신청 (계약서 §4-3, v1.11.0).
 *
 * 상태 전이는 매칭 신청(§6)과 같은 모양이다 — PENDING 에서만 수락·거절·취소로 갈라지고,
 * 거절·취소된 이력은 재신청을 막지 않는다. 그래서 (team, user) 조합의 행이 여러 개일 수
 * 있고, "대기 중인 신청은 하나뿐"이라는 규칙만 지켜지면 된다.
 */
@Entity
@Getter
@Table(name = "team_join_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamJoinRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinStatus status;

    @Builder
    private TeamJoinRequest(Team team, User user, String message) {
        this.team = team;
        this.user = user;
        this.message = message;
        this.status = JoinStatus.PENDING;
    }

    public boolean isPending() {
        return status == JoinStatus.PENDING;
    }

    public void accept() {
        this.status = JoinStatus.ACCEPTED;
    }

    public void reject() {
        this.status = JoinStatus.REJECTED;
    }

    public void cancel() {
        this.status = JoinStatus.CANCELED;
    }

    public boolean isRequestedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
