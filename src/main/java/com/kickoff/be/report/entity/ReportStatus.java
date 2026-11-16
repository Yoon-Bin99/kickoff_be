package com.kickoff.be.report.entity;

/**
 * 신고의 처리 상태 (계약서 §1·§7-2, v1.35.0).
 *
 * {@link #TEMP_HIDDEN} 이 따로 있는 이유: 임시 가림은 <b>결론이 아니다</b>. 30일 안에
 * 삭제나 조치 없음으로 다시 정해야 하고, 그때까지 운영자 목록에 남아 있어야 한다.
 * CLOSED 로 묶어 버리면 기한이 다가오는 건을 아무도 보지 않게 된다.
 */
public enum ReportStatus {

    OPEN,
    TEMP_HIDDEN,
    CLOSED;

    /** 아직 처리 전인지 — 중복 신고 판정과 신고자 수 집계가 이 기준을 쓴다. */
    public boolean isPending() {
        return this != CLOSED;
    }
}
