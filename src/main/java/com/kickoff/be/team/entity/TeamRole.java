package com.kickoff.be.team.entity;

/**
 * 팀 안에서의 역할 (계약서 §4-2, v1.9.0).
 *
 * 응답에서 <b>무관계는 null</b>로 나간다 — NONE 같은 값을 두지 않은 건 계약이 null 로
 * 못박고 있어서다. 그래서 이 열거형에는 관계가 있는 경우만 있다.
 *
 * 서열은 선언 순서 그대로 OWNER > ADMIN > MEMBER 이고, 판정도 이 순서로 한다
 * (계약서 §4-3, v1.11.0). MEMBER 는 조회·소속 표시만 갖는다 — 팀 페이지 수정도,
 * 매칭·리뷰도 못 한다.
 */
public enum TeamRole {
    OWNER, ADMIN, MEMBER
}
