package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.TeamJoinRequest;
import java.time.OffsetDateTime;

/**
 * 소유자·관리자가 보는 가입 신청 한 건 (계약서 §4-3, v1.11.0).
 *
 * 신청자 본인이 받는 {@link TeamJoinResponse} 와 모양이 다르다 — 이쪽은 "누가 신청했나"가
 * 필요하고, 저쪽은 "내 신청이 어떻게 됐나"가 필요하다.
 */
public record TeamJoinRequestItem(Long id, Applicant applicant, String message,
                                  OffsetDateTime createdAt) {

    public record Applicant(Long userId, String nickname) {
    }

    public static TeamJoinRequestItem of(TeamJoinRequest request) {
        return new TeamJoinRequestItem(
                request.getId(),
                new Applicant(request.getUser().getId(), request.getUser().getNickname()),
                request.getMessage(),
                request.getCreatedAt());
    }
}
