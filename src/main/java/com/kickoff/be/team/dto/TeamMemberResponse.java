package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Position;
import com.kickoff.be.team.entity.TeamMember;

/** 팀원 한 명 (계약서 §4-1, v1.8.0). */
public record TeamMemberResponse(Long id, String name, Position position, Integer backNumber) {

    public static TeamMemberResponse of(TeamMember member) {
        return new TeamMemberResponse(member.getId(), member.getName(),
                member.getPosition(), member.getBackNumber());
    }
}
