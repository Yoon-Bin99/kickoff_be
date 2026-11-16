package com.kickoff.be.block.dto;

import com.kickoff.be.block.entity.TeamBlock;
import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.team.dto.TeamSummary;
import java.time.OffsetDateTime;

/** 차단 목록의 한 줄 (계약서 §7-2). */
public record BlockResponse(TeamSummary team, OffsetDateTime createdAt) {

    /** 평점은 호출자가 배치로 모아 넘긴다 (계약서 §2, v1.10.0) — 줄마다 조회하면 N+1 이다. */
    public static BlockResponse of(TeamBlock block, ReviewStats stats) {
        return new BlockResponse(TeamSummary.of(block.getBlockedTeam(), stats),
                block.getCreatedAt());
    }
}
