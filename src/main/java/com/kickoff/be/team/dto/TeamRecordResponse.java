package com.kickoff.be.team.dto;

import com.kickoff.be.team.entity.MatchResult;
import com.kickoff.be.team.entity.TeamRecord;
import java.time.LocalDate;

/** 경기 기록 한 건 (계약서 §4-1, v1.8.0). result 는 스코어에서 계산된 값이다. */
public record TeamRecordResponse(
        Long id,
        LocalDate playedOn,
        String opponentName,
        int ourScore,
        int opponentScore,
        MatchResult result,
        String memo,
        /** 매칭에서 만든 기록이면 그 매칭 id, 손으로 넣었으면 null (계약서 §4-1, v1.10.0). */
        Long requestId
) {

    public static TeamRecordResponse of(TeamRecord record) {
        return new TeamRecordResponse(record.getId(), record.getPlayedOn(),
                record.getOpponentName(), record.getOurScore(), record.getOpponentScore(),
                record.getResult(), record.getMemo(), record.getRequestId());
    }
}
