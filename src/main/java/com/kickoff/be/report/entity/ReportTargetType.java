package com.kickoff.be.report.entity;

/**
 * 신고할 수 있는 것 (계약서 §1·§7-2, v1.35.0).
 *
 * <b>자동 임시 가림이 되는 것과 아닌 것이 갈린다.</b> 공개 게시물(모집글·리뷰)은 여러
 * 사람이 보고 신고가 쌓이면 운영자 확인 전에도 가리지만({@link #autoHidable}),
 * 팀과 채팅은 그러지 않는다 — 팀을 통째로 가리면 그 팀과 매칭이 잡힌 상대까지 화면을
 * 잃고, 채팅은 1:1 대화라 애초에 "가린다"가 성립하지 않는다(차단과 이용 제한으로 다룬다).
 */
public enum ReportTargetType {

    POST(true, "모집글"),
    REVIEW(true, "리뷰"),
    /** 팀 소개·팀 이름, 그리고 그 팀의 행동 전반. 조치는 소개 가림이나 감독 이용 제한이다. */
    TEAM(false, "팀"),
    /** 메시지 id. {@code requestId} 가 함께 필요하다 — 메시지는 방 안에서만 식별된다. */
    CHAT_MESSAGE(false, "채팅 메시지");

    private final boolean autoHidable;
    private final String displayName;

    ReportTargetType(boolean autoHidable, String displayName) {
        this.autoHidable = autoHidable;
        this.displayName = displayName;
    }

    /** 운영자 푸시에 쓰는 표시명 — 사용자 화면의 문구는 FE 가 가진다. */
    public String displayName() {
        return displayName;
    }

    /** 서로 다른 신고자 3명이 쌓였을 때 자동으로 가릴 수 있는 대상인지 (계약서 §7-2). */
    public boolean isAutoHidable() {
        return autoHidable;
    }
}
