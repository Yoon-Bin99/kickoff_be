package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.TeamAdmin;
import java.time.OffsetDateTime;

/** 팀 관리자 한 명 (계약서 §4-2, v1.9.0). */
public record TeamAdminResponse(Long userId, String nickname, OffsetDateTime grantedAt) {

    public static TeamAdminResponse of(TeamAdmin admin) {
        return new TeamAdminResponse(admin.getUser().getId(),
                admin.getUser().getNickname(), admin.getGrantedAt());
    }
}
