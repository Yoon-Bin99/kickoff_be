package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Position;
import com.kickoff.be.team.entity.TeamMember;

/**
 * 팀원 한 명 (계약서 §4-1, v1.8.0).
 *
 * v1.11.0 부터 userId 가 함께 나간다 — 앱 계정과 연결된 항목이면 그 사용자 id, 수기로 적은
 * 명단이면 null 이다. FE 는 이걸로 "가입한 멤버"와 "주장이 적어 둔 이름"을 구분한다.
 */
public record TeamMemberResponse(Long id, String name, Position position, Integer backNumber,
                                 Long userId) {

    public static TeamMemberResponse of(TeamMember member) {
        return new TeamMemberResponse(member.getId(), member.getName(),
                member.getPosition(), member.getBackNumber(), member.getUserId());
    }
}
