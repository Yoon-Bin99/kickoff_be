package com.kickoff.be.matchrequest.entity;

/**
 * 계약서 §1 — 대기중/수락됨/거절됨/취소됨/매칭취소됨.
 *
 * <b>CANCELED 와 MATCH_CANCELED 는 다르다</b> (계약서 §6-2, v1.20.0).
 * CANCELED 는 <b>수락 전</b> 신청 팀이 스스로 신청을 무른 것이고, MATCH_CANCELED 는
 * <b>수락 후</b> 양 팀 중 한쪽이 성사된 매칭을 깬 것이다. 둘을 한 값으로 합치면 화면에서
 * "신청 철회"와 "매칭 파기"가 같아 보이는데, 상대 팀 입장에서 무게가 전혀 다르다.
 *
 * 값을 더할 때는 <b>이 열거형을 읽는 모든 규칙</b>을 같이 봐야 한다. 컴파일은 통과하면서
 * 규칙만 조용히 어긋나는 자리가 많다 — 중복 신청 판정(MatchRequestService.ACTIVE),
 * requestCount 집계, 채팅방 목록, 정렬의 PENDING 우선 조건이 전부 이 값을 본다.
 */
public enum RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    /** 수락 전, 신청 팀이 스스로 철회. */
    CANCELED,
    /** 수락 후, 양 팀 중 한쪽이 매칭을 취소 (계약서 §6-2, v1.20.0). */
    MATCH_CANCELED
}
