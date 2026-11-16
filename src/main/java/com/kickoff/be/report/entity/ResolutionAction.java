package com.kickoff.be.report.entity;

/**
 * 운영자의 조치 (계약서 §7-2 — {@code POST /api/admin/reports/{id}/resolve}, v1.35.0).
 *
 * <b>{@link #HIDE} 만 결론이 아니다.</b> 30일 안에 삭제나 조치 없음으로 다시 정해야 하고,
 * 그때까지 신고는 {@code TEMP_HIDDEN} 으로 운영자 목록에 남는다.
 */
public enum ResolutionAction {

    /** 조치하지 않는다. 가려져 있었다면 복원한다. */
    DISMISS("조치 없음"),

    /** 임시 가림 30일. */
    HIDE("임시 가림"),

    REMOVE("삭제"),

    /** 콘텐츠는 두고 계정만 제한한다. */
    SUSPEND_ONLY("이용 제한");

    private final String displayName;

    ResolutionAction(String displayName) {
        this.displayName = displayName;
    }

    /** 통지에 싣는 조치 이름 (계약서 §7-2 — 법무 초안 3-3절). */
    public String displayName() {
        return displayName;
    }

    public boolean hidesContent() {
        return this == HIDE;
    }

    public boolean removesContent() {
        return this == REMOVE;
    }
}
