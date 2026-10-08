package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Formation;
import com.kickoff.be.team.entity.Squad;
import java.time.OffsetDateTime;

/**
 * 스쿼드 목록 항목 (계약서 §4-4, v1.28.0).
 *
 * 자리 내용은 넣지 않는다 — 계약서가 목록에 squadId·title·formation·updatedAt 만 두었다.
 * 목록에서 항목까지 실으면 30개 × 최대 21자리를 매번 끌고 오게 되고, 화면은 제목만 보여 준다.
 */
public record SquadSummary(
        Long squadId,
        String title,
        Formation formation,
        OffsetDateTime updatedAt
) {

    public static SquadSummary of(Squad squad) {
        return new SquadSummary(squad.getId(), squad.getTitle(), squad.getFormation(),
                squad.getUpdatedAt());
    }
}
