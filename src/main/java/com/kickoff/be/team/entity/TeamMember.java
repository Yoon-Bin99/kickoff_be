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
 * 팀원 명단 (계약서 §4-1, v1.8.0).
 *
 * <b>앱 계정이 아니다.</b> 주장이 손으로 입력하는 정보라 User 와 연결되지 않는다 — 명단에
 * 있는 사람이 앱을 안 깔았을 수도 있고, 같은 이름이 둘일 수도 있다. 그래서 이름은 유일하지
 * 않고, 삭제도 그냥 지우면 된다.
 */
@Entity
@Getter
@Table(name = "team_members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 20)
    private String name;

    /** nullable — 포지션을 안 정한 팀원이 있다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Position position;

    /** nullable — 등번호가 없는 팀원이 있다. 목록에서는 뒤로 밀린다. */
    private Integer backNumber;

    /**
     * 이 항목이 가리키는 앱 계정 (계약서 §4-3, v1.11.0). 수기 명단은 <b>null</b> 이다.
     *
     * 연결된 항목은 곧 그 사람의 팀 소속이기도 하다 — 항목을 지우면 멤버십도 끝난다(강퇴).
     * name 은 닉네임을 따르므로 PATCH 로 못 바꾼다. 둘이 갈리면 명단에 옛 이름이 남아
     * 같은 사람이 두 명처럼 보인다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Builder
    private TeamMember(Team team, String name, Position position, Integer backNumber,
                       User user) {
        this.team = team;
        this.name = name;
        this.position = position;
        this.backNumber = backNumber;
        this.user = user;
    }

    /** 앱 계정과 연결된 항목인지 (계약서 §4-3). 연결됐으면 그 사람은 이 팀 소속이다. */
    public boolean isLinkedToAccount() {
        return user != null;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    /** 닉네임이 바뀌면 명단 이름도 따라간다 (계약서 §4-3). */
    public void syncNameFromAccount(String nickname) {
        this.name = nickname;
    }

    /** 이름은 지울 수 없어서 여기서는 null 이 "안 건드림"이다 (계약서 §4-1). */
    public void updateName(String name) {
        if (name != null) {
            this.name = name;
        }
    }

    /** null 이면 포지션 없음으로 되돌린다 (v1.5.1 지우기 규칙). */
    public void updatePosition(Position position) {
        this.position = position;
    }

    /** null 이면 등번호 없음으로 되돌린다 (v1.5.1 지우기 규칙). */
    public void updateBackNumber(Integer backNumber) {
        this.backNumber = backNumber;
    }

    public boolean belongsTo(Long teamId) {
        return team.getId().equals(teamId);
    }
}
