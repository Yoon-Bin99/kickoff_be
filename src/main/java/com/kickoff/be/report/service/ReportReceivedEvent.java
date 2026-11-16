package com.kickoff.be.report.service;

import com.kickoff.be.report.entity.Report;

/**
 * 신고가 접수됐다 — 운영자에게 알린다 (계약서 §7-2 「신고의 즉시 효과」 3).
 *
 * 푸시 발송은 커밋 뒤에 비동기로 한다({@code ReportPushListener}) — 롤백된 신고의 유령
 * 알림이 나가지 않고, Expo 왕복이 신고 응답을 붙잡지 않는다.
 *
 * <b>내용을 싣지 않는다.</b> 운영자 푸시에 신고된 글이나 대화가 그대로 뜨면 잠금화면에
 * 남의 대화가 보인다. 사유와 대상 종류까지만 보내고, 나머지는 운영자 화면에서 본다.
 */
public record ReportReceivedEvent(Long reportId, String reasonName, String targetName) {

    /** payload 의 {@code data.type}. 바꾸면 FE 딥링크가 깨진다. */
    public static final String TYPE = "REPORT";

    public static ReportReceivedEvent of(Report report) {
        return new ReportReceivedEvent(report.getId(), report.getReason().displayName(),
                report.getTargetType().displayName());
    }
}
