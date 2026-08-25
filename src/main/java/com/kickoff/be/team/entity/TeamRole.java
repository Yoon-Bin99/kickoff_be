package com.kickoff.be.team.entity;

/**
 * 팀 안에서의 역할 (계약서 §4-2, v1.9.0).
 *
 * 응답에서 <b>무관계는 null</b>로 나간다 — NONE 같은 값을 두지 않은 건 계약이 null 로
 * 못박고 있어서다. 그래서 이 열거형에는 관계가 있는 두 경우만 있다.
 */
public enum TeamRole {
    OWNER, ADMIN
}
