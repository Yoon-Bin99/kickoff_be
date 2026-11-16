package com.kickoff.be.report.repository;

import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportTargetType;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 같은 계정이 같은 대상을 이미 신고했고 아직 처리 전인지 (계약서 §7-2 — 409). */
    @Query("""
            select count(r) > 0 from Report r
            where r.reporterUserId = :userId
              and r.targetType = :targetType and r.targetId = :targetId
              and r.status <> com.kickoff.be.report.entity.ReportStatus.CLOSED
            """)
    boolean existsPendingBy(@Param("userId") Long userId,
                            @Param("targetType") ReportTargetType targetType,
                            @Param("targetId") Long targetId);

    /** 최근 24시간 동안 이 계정이 넣은 신고 수 (계약서 §7-2 — 하루 10건). */
    @Query("select count(r) from Report r where r.reporterUserId = :userId "
            + "and r.createdAt >= :since")
    long countSince(@Param("userId") Long userId, @Param("since") OffsetDateTime since);

    /**
     * 이 대상에 걸린 처리 전 신고들. 자동 가림 판정과 운영자 목록의 {@code reporterCount}
     * 가 쓴다 — <b>같은 팀의 신고는 1명으로</b> 세야 해서 수가 아니라 행을 가져온다.
     */
    @Query("""
            select r from Report r
            where r.targetType = :targetType and r.targetId = :targetId
              and r.status <> com.kickoff.be.report.entity.ReportStatus.CLOSED
            """)
    List<Report> findPendingOf(@Param("targetType") ReportTargetType targetType,
                               @Param("targetId") Long targetId);

    /**
     * 이 계정이 신고해 둔 대상들의 id (계약서 §7-2 「신고의 즉시 효과」 1).
     *
     * 신고한 사람 화면에서는 운영자 조치 전이라도 바로 빠진다 — 신고까지 한 글이 계속
     * 눈앞에 있으면 신고가 아무 일도 하지 않은 것처럼 보인다.
     */
    @Query("""
            select r.targetId from Report r
            where r.reporterUserId = :userId and r.targetType = :targetType
              and r.status <> com.kickoff.be.report.entity.ReportStatus.CLOSED
            """)
    List<Long> findPendingTargetIdsBy(@Param("userId") Long userId,
                                      @Param("targetType") ReportTargetType targetType);

    List<Report> findByReporterUserId(Long reporterUserId);

    List<Report> findByReportedUserId(Long reportedUserId);
}
