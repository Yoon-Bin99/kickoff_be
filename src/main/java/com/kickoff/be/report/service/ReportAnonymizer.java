package com.kickoff.be.report.service;

import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.repository.ReportChatMessageRepository;
import com.kickoff.be.report.repository.ReportRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴할 때 신고 기록에서 그 사람을 지운다 (계약서 §7-2 「기록의 보관」, v1.35.0).
 *
 * <b>신고 건 자체는 남고 사람만 사라진다.</b> 대상 종류·사유·조치는 보관 기간까지 남아야
 * 운영 이력이 끊기지 않지만, 누가 신고했고 누가 신고됐는지는 탈퇴와 함께 지울 값이다.
 *
 * <b>채팅 문맥 사본은 통째로 지운다.</b> "대화는 탈퇴와 함께 지운다"(§3-4)는 약속이
 * 사본에도 그대로 적용된다 — 신고 기록에 들어 있다고 해서 예외가 되지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ReportAnonymizer {

    private final ReportRepository reportRepository;
    private final ReportChatMessageRepository chatMessageRepository;

    @Transactional
    public void forget(Long userId) {
        List<Long> chatReportIds = new ArrayList<>();
        for (Report report : reportRepository.findByReporterUserId(userId)) {
            report.forgetReporter();
            chatReportIds.add(report.getId());
        }
        for (Report report : reportRepository.findByReportedUserId(userId)) {
            report.forgetReported();
            chatReportIds.add(report.getId());
        }
        if (!chatReportIds.isEmpty()) {
            chatMessageRepository.deleteByReportIdIn(chatReportIds);
        }
    }
}
