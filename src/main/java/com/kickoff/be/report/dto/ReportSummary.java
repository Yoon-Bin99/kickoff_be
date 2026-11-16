package com.kickoff.be.report.dto;

import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportReason;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.entity.ReportTargetType;
import java.time.OffsetDateTime;

/**
 * 신고함 목록의 한 줄 (계약서 §7-2, v1.35.0).
 *
 * @param reporterCount 같은 대상에 걸린 처리 전 신고의 <b>서로 다른 신고자 수</b>
 *                      (같은 팀은 1명). 운영자가 "몇 명이 같은 것을 신고했는지"로
 *                      급한 건을 가린다
 * @param hiddenUntil   지금 임시 가림 중이면 그 기한 (v1.35.2) — 기한이 임박한 건을
 *                      목록에서 알아볼 수 있어야 한다. 아니면 null
 */
public record ReportSummary(
        Long id,
        ReportTargetType targetType,
        Long targetId,
        ReportReason reason,
        ReportStatus status,
        long reporterCount,
        boolean autoHidden,
        OffsetDateTime hiddenUntil,
        OffsetDateTime createdAt
) {

    public static ReportSummary of(Report report, long reporterCount,
                                   OffsetDateTime hiddenUntil) {
        return new ReportSummary(report.getId(), report.getTargetType(), report.getTargetId(),
                report.getReason(), report.getStatus(), reporterCount, report.isAutoHidden(),
                hiddenUntil, report.getCreatedAt());
    }
}
