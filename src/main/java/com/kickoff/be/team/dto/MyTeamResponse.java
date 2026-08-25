package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.entity.TeamRole;

/**
 * 내가 소유·관리하는 팀 한 곳 (계약서 §4-2, v1.9.1).
 *
 * 마이 탭 진입점용이다. 이게 없으면 관리자로 임명된 사람이 그 팀을 되찾아갈 길이
 * 없다 — 소유 팀은 {@code GET /api/teams/me} 로 찾지만 관리 팀은 어디에도 안 걸린다.
 */
public record MyTeamResponse(TeamSummary team, TeamRole role) {

    public static MyTeamResponse of(Team team, TeamRole role) {
        return new MyTeamResponse(TeamSummary.from(team), role);
    }
}
