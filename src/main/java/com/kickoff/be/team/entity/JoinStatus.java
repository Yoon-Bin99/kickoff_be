package com.kickoff.be.team.entity;

/**
 * 팀 가입 신청 상태 (계약서 §4-3, v1.11.0).
 *
 * 매칭 신청(RequestStatus)과 값이 같지만 열거형을 따로 둔다. 둘은 서로 다른 도메인의
 * 상태이고, 한쪽에 값이 늘어날 때 다른 쪽까지 끌려가면 안 된다.
 */
public enum JoinStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED
}
