package com.kickoff.be.block.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import java.time.OffsetDateTime;
import lombok.Getter;

/**
 * 차단하려는 팀과 예정된 경기가 있다 (계약서 §7-2 — 409 {@code BLOCK_HAS_UPCOMING_MATCH}).
 *
 * <b>이 하나만 공통 에러 형식에 칸이 하나 더 붙는다</b>(v1.35.2 — {@code matchAt}).
 * FE 확인창이 "11월 22일 경기가 취소됩니다"라고 말할 수 있어야 해서다. 공통 응답에
 * nullable 칸을 하나 더 다는 대신 이 예외만 따로 그린다 — 그러지 않으면 모든 에러 응답에
 * 의미 없는 {@code "matchAt": null} 이 붙는다.
 */
@Getter
public class BlockHasUpcomingMatchException extends BusinessException {

    private final OffsetDateTime matchAt;

    public BlockHasUpcomingMatchException(OffsetDateTime matchAt) {
        super(ErrorCode.BLOCK_HAS_UPCOMING_MATCH);
        this.matchAt = matchAt;
    }
}
