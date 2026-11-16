package com.kickoff.be.report.entity;

/**
 * 명예훼손 신고에서 "누구의 권리가 침해됐는지" (계약서 §7-2, v1.35.0).
 *
 * 본인·자기 팀이면 정보통신망법 제44조의2의 <b>삭제 요청</b>으로 다뤄야 한다 — 그쪽은
 * 임시조치와 통지 의무가 따라붙는다. 제3자를 대신한 신고는 일반 신고로 본다.
 */
public enum RightsHolder {

    ME,
    MY_TEAM,
    OTHER;

    /** 법정 삭제 요청으로 다룰 신고인지 (제44조의2 제1항 — "자기의 권리가 침해된 자"). */
    public boolean isSelf() {
        return this == ME || this == MY_TEAM;
    }
}
