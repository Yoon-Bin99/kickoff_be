package com.kickoff.be.operator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영자가 무엇을 했는지 (계약서 §7-2 「감사 기록」, v1.35.1).
 *
 * 개인정보 안전성 확보조치 기준 제8조의 접속기록이다 — 누가·언제·어디서·누구의 정보를·
 * 무엇을 했는지. <b>내용은 적지 않는다</b>: 문의 본문이나 대화를 여기 옮기면 접속기록이
 * 그 자체로 또 하나의 개인정보 더미가 된다.
 *
 * <b>대상 이용자가 탈퇴해도 기간까지 남는다</b>(법령상 보관). 다른 기록과 반대 방향이라
 * 처리방침에 그렇게 적는다. 조회 API 는 없다 — 운영자가 DB 에서 본다.
 */
@Entity
@Getter
@Table(name = "operator_audit_logs",
        indexes = @Index(name = "idx_operator_audit_logs_created", columnList = "created_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_user_id")
    private Long operatorUserId;

    /** 수행한 일. 경로가 아니라 뜻을 적는다 — "문의방 열람"이지 "GET /api/support/..." 가 아니다. */
    @Column(nullable = false, length = 50)
    private String action;

    /** 대상이 된 이용자. 목록 조회처럼 특정 대상이 없으면 null (계약서 §7-2). */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** 대상 식별값 — 신고 id 등. */
    @Column(name = "target_id")
    private Long targetId;

    /** 접속 IP. 프록시 뒤라면 X-Forwarded-For 의 첫 값이다. */
    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private OperatorAuditLog(Long operatorUserId, String action, Long targetUserId,
                             Long targetId, String ip) {
        this.operatorUserId = operatorUserId;
        this.action = action;
        this.targetUserId = targetUserId;
        this.targetId = targetId;
        this.ip = ip;
        this.createdAt = OffsetDateTime.now();
    }
}
