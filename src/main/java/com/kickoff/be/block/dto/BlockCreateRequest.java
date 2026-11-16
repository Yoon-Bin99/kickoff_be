package com.kickoff.be.block.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 차단 요청 (계약서 §7-2 — {@code POST /api/blocks}, v1.35.0).
 *
 * @param cancelUpcomingMatch 예정된 매칭이 있을 때 취소까지 함께 할지. 없으면 false 로
 *                            본다 — 기본이 "취소함"이면 확인창 없이 경기가 사라진다.
 */
public record BlockCreateRequest(

        @NotNull(message = "차단할 팀이 필요합니다.")
        Long teamId,

        Boolean cancelUpcomingMatch
) {

    public boolean cancels() {
        return Boolean.TRUE.equals(cancelUpcomingMatch);
    }
}
