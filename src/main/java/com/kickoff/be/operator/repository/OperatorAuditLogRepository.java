package com.kickoff.be.operator.repository;

import com.kickoff.be.operator.entity.OperatorAuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorAuditLogRepository extends JpaRepository<OperatorAuditLog, Long> {

    /** 1년 뒤 지운다 (계약서 §7-2 — 하루 한 번). */
    long deleteByCreatedAtBefore(OffsetDateTime cutoff);
}
