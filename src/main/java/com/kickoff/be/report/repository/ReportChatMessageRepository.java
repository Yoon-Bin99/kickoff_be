package com.kickoff.be.report.repository;

import com.kickoff.be.report.entity.ReportChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportChatMessageRepository extends JpaRepository<ReportChatMessage, Long> {

    List<ReportChatMessage> findByReportIdOrderBySentAtAsc(Long reportId);

    /** 탈퇴하면 대화 사본은 지운다 (계약서 §7-2 「기록의 보관」·§3-4). */
    void deleteByReportIdIn(List<Long> reportIds);
}
