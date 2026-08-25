package com.kickoff.be.team.entity;

import com.kickoff.be.common.BaseTimeEntity;
import com.kickoff.be.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 팀 관리자 임명 (계약서 §4-2, v1.9.0).
 *
 * 소유자와 달리 <b>여러 명</b>이고(팀당 5명), 한 사용자가 여러 팀의 관리자일 수 있다.
 * 자기 팀 소유 여부와도 무관하다 — 팀이 없는 사용자도 남의 팀 관리자가 될 수 있어서,
 * 팀 페이지 쓰기 경로에는 팀 보유 검사를 걸지 않는다.
 *
 * 소유자는 이 표에 들어가지 않는다. 소유자는 teams.owner_id 로 정해지고 유일해서,
 * 여기에 같이 넣으면 "소유자이면서 관리자"라는 애매한 상태가 생긴다.
 */
@Entity
@Getter
@Table(name = "team_admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamAdmin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    private TeamAdmin(Team team, User user) {
        this.team = team;
        this.user = user;
    }

    /** 계약서의 grantedAt. 임명 시각이 곧 생성 시각이라 따로 컬럼을 두지 않는다. */
    public OffsetDateTime getGrantedAt() {
        return getCreatedAt();
    }
}
