package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.Formation;
import com.kickoff.be.team.entity.Squad;
import java.time.OffsetDateTime;
import java.util.List;

/** 스쿼드 단건 (계약서 §4-4, v1.28.0). 식별자가 {@code id} 가 아니라 {@code squadId} 다. */
public record SquadResponse(
        Long squadId,
        Long teamId,
        String title,
        Formation formation,
        List<SquadItem> slots,
        List<SquadItem> bench,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static SquadResponse of(Squad squad) {
        return new SquadResponse(
                squad.getId(),
                squad.getTeam().getId(),
                squad.getTitle(),
                squad.getFormation(),
                squad.starters().stream().map(SquadItem::starter).toList(),
                squad.bench().stream().map(SquadItem::benched).toList(),
                squad.getCreatedAt(),
                squad.getUpdatedAt());
    }
}
