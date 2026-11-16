package com.kickoff.be.operator.service;

import com.kickoff.be.operator.entity.OperatorAuditLog;
import com.kickoff.be.operator.repository.OperatorAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 접속기록 한 줄을 남긴다 (계약서 §7-2 「감사 기록」, v1.35.1).
 *
 * <b>{@link OperatorGuard} 와 빈을 나눈 이유</b>: 같은 빈 안에서 부르면 프록시를 타지
 * 않아 {@code REQUIRES_NEW} 가 무시된다. 그러면 호출자의 트랜잭션에 그대로 올라타는데,
 * 운영자 조회는 대부분 {@code readOnly = true} 라 <b>PostgreSQL 이 INSERT 를 거부한다</b>
 * (H2 는 읽기 전용을 강제하지 않아 그냥 통과한다 — postgresTest 가 아니면 못 잡았다).
 *
 * <b>REQUIRES_NEW 인 이유</b>: 조치가 롤백돼도 "누가 무엇을 시도했는지"는 남아야 한다.
 * 접속기록은 성공한 일의 기록이 아니라 접근의 기록이다.
 */
@Component
@RequiredArgsConstructor
public class OperatorAuditRecorder {

    private final OperatorAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long operatorUserId, String action, Long targetUserId, Long targetId,
                       String ip) {
        auditLogRepository.save(OperatorAuditLog.builder()
                .operatorUserId(operatorUserId)
                .action(action)
                .targetUserId(targetUserId)
                .targetId(targetId)
                .ip(ip)
                .build());
    }
}
