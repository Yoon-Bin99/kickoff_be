package com.kickoff.be.report.service;

import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.repository.ReportChatMessageRepository;
import com.kickoff.be.report.repository.ReportRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 기록을 기간이 지나면 지운다 (계약서 §7-2 「기록의 보관」, v1.35.0).
 *
 * <b>기간이 둘인 이유.</b> 조치가 있었던 신고는 분쟁의 근거가 될 수 있어 1년을 두고,
 * 조치 없이 끝난 신고는 90일이면 충분하다 — 아무 일도 일어나지 않은 신고를 1년 들고
 * 있을 근거가 없다(개인정보 보호법 제21조 — 목적을 달성하면 지체 없이 파기).
 *
 * 처리 전 신고는 지우지 않는다. 운영자가 아직 보지 않은 건이 사라지면 신고가 조용히
 * 없어진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportRetentionJob {

    /** 조치가 있었던 신고. */
    private static final Duration RESOLVED_RETENTION = Duration.ofDays(365);

    /** 조치 없이 끝난 신고. */
    private static final Duration DISMISSED_RETENTION = Duration.ofDays(90);

    private final ReportRepository reportRepository;
    private final ReportChatMessageRepository chatMessageRepository;

    /** 다른 파기 작업과 같은 시각이다 (새벽 4시 30분 KST). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredReports() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Report> expired = reportRepository.findByStatus(ReportStatus.CLOSED).stream()
                .filter(report -> isExpired(report, now))
                .toList();
        if (expired.isEmpty()) {
            return;
        }
        List<Long> ids = expired.stream().map(Report::getId).toList();
        chatMessageRepository.deleteByReportIdIn(ids);
        reportRepository.deleteAll(expired);

        // 건수만 남긴다. 무엇을 지웠는지 적으면 파기 목적으로 도는 작업이 그 값을 로그에
        // 다시 심는 꼴이 된다.
        log.info("보관 기간 경과 신고 파기 — {}건", ids.size());
    }

    /**
     * 조치 여부로 기간이 갈린다.
     *
     * {@code resolvedAt} 이 없는 CLOSED 는 없다고 봐야 하지만, 있다면 가장 짧은 기간을
     * 적용한다 — 근거를 알 수 없는 기록을 더 오래 들고 있을 이유가 없다.
     */
    private boolean isExpired(Report report, OffsetDateTime now) {
        OffsetDateTime resolvedAt = report.getResolvedAt();
        if (resolvedAt == null) {
            return report.getCreatedAt().isBefore(now.minus(DISMISSED_RETENTION));
        }
        Duration retention = isDismissed(report) ? DISMISSED_RETENTION : RESOLVED_RETENTION;
        return resolvedAt.isBefore(now.minus(retention));
    }

    private boolean isDismissed(Report report) {
        return report.getResolutionAction() == null
                || report.getResolutionAction().equals("DISMISS");
    }
}
