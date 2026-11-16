package com.kickoff.be.report.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.report.dto.ReportDetail;
import com.kickoff.be.report.dto.ReportSummary;
import com.kickoff.be.report.dto.ResolveRequest;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.service.AdminReportService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고함 (계약서 §7-2 「운영자 API」, v1.35.0·v1.35.1).
 *
 * 모든 호출이 {@link OperatorGuard} 를 지난다 — 운영자 계정인지(403), 운영자 세션이
 * 있는지(401), 그리고 <b>접속기록</b>까지 한 줄로 끝난다. 나누면 "검사는 했는데 기록은
 * 안 남는" 경로가 생긴다.
 */
@RestController
@RequiredArgsConstructor
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final OperatorGuard operatorGuard;

    @GetMapping("/api/admin/reports")
    public ResponseEntity<PageResponse<ReportSummary>> list(
            @LoginUser User user,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 목록에는 대상이 된 이용자가 없다 — 그래서 targetUserId 는 null 이다 (계약서 §7-2).
        operatorGuard.require(user, "신고 목록", null, null);
        return ResponseEntity.ok(adminReportService.list(status, page, size));
    }

    @GetMapping("/api/admin/reports/{reportId}")
    public ResponseEntity<ReportDetail> detail(@LoginUser User user,
                                               @PathVariable Long reportId) {
        // 열람 자체가 기록 대상이다 (계약서 §7-2 — "서버는 이 조회를 열람 기록으로 남긴다").
        operatorGuard.require(user, "신고 열람", null, reportId);
        return ResponseEntity.ok(adminReportService.detail(reportId));
    }

    @PostMapping("/api/admin/reports/{reportId}/resolve")
    public ResponseEntity<ReportDetail> resolve(@LoginUser User user,
                                                @PathVariable Long reportId,
                                                @Valid @RequestBody ResolveRequest request) {
        operatorGuard.require(user, "신고 조치", null, reportId);
        return ResponseEntity.ok(adminReportService.resolve(reportId, request));
    }

    @DeleteMapping("/api/admin/users/{userId}/suspension")
    public ResponseEntity<Void> clearSuspension(@LoginUser User user,
                                                @PathVariable Long userId) {
        operatorGuard.require(user, "이용 제한 해제", userId, null);
        adminReportService.clearSuspension(userId);
        return ResponseEntity.noContent().build();
    }
}
