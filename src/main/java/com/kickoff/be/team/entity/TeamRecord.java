package com.kickoff.be.team.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수동 입력 경기 기록 (계약서 §4-1, v1.8.0).
 *
 * 매칭 시스템과 자동으로 엮이지 않는다 — 앱 밖에서 뛴 경기가 대부분이라, 앱 안의 매칭만
 * 기록되면 명단이 비어 보인다.
 *
 * 수정이 없고 삭제만 있다 (계약서 §4-1). 잘못 넣었으면 지우고 다시 넣는다.
 */
@Entity
@Getter
@Table(name = "team_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private LocalDate playedOn;

    /** 상대 팀 이름도 자유 문자열이다. 앱에 없는 팀과 뛴 경기가 대부분이다. */
    @Column(nullable = false, length = 30)
    private String opponentName;

    @Column(nullable = false)
    private int ourScore;

    @Column(nullable = false)
    private int opponentScore;

    @Column(length = 200)
    private String memo;

    @Builder
    private TeamRecord(Team team, LocalDate playedOn, String opponentName,
                       int ourScore, int opponentScore, String memo) {
        this.team = team;
        this.playedOn = playedOn;
        this.opponentName = opponentName;
        this.ourScore = ourScore;
        this.opponentScore = opponentScore;
        this.memo = memo;
    }

    /** 저장하지 않고 스코어에서 계산한다 — 자세한 이유는 {@link MatchResult} 에 적어 뒀다. */
    public MatchResult getResult() {
        return MatchResult.of(ourScore, opponentScore);
    }

    public boolean belongsTo(Long teamId) {
        return team.getId().equals(teamId);
    }
}
