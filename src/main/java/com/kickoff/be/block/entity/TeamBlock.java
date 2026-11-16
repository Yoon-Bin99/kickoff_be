package com.kickoff.be.block.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 차단 (계약서 §7-2 「차단」, v1.35.0).
 *
 * <b>계정이 팀을 차단한다.</b> 단위가 엇갈려 보이지만 제품의 모양이 그렇다 — 화면에서
 * 치우고 싶은 것은 그 팀의 글·리뷰이고, 치우는 주체는 그것을 보는 사람이다. 감독이
 * 차단하면 두 팀 사이의 일(신청·채팅)까지 막히지만, 그건 그 사람이 감독이기 때문에
 * 따라오는 효과다.
 *
 * <b>상대에게 알리지 않는다.</b> 차단당한 쪽 화면은 아무것도 달라지지 않는다 — 신청이
 * 막힐 때도 "지금은 이 팀에 신청할 수 없습니다"라고만 한다.
 */
@Entity
@Getter
@Table(name = "team_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_team_blocks_user_team",
                columnNames = {"blocker_user_id", "blocked_team_id"}),
        indexes = @Index(name = "idx_team_blocks_blocked_team", columnList = "blocked_team_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_user_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_team_id", nullable = false)
    private Team blockedTeam;

    @Builder
    private TeamBlock(User blocker, Team blockedTeam) {
        this.blocker = blocker;
        this.blockedTeam = blockedTeam;
    }
}
