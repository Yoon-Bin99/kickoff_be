package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.JoinStatus;
import com.kickoff.be.team.entity.TeamJoinRequest;
import java.time.OffsetDateTime;

/** 가입 신청 (계약서 §4-3, v1.11.0). 신청자 본인이 받는 형태. */
public record TeamJoinResponse(
        Long id,
        Long teamId,
        JoinStatus status,
        String message,
        OffsetDateTime createdAt
) {

    public static TeamJoinResponse of(TeamJoinRequest request) {
        return new TeamJoinResponse(request.getId(), request.getTeam().getId(),
                request.getStatus(), request.getMessage(), request.getCreatedAt());
    }
}
