package com.kickoff.be.support.entity;

/**
 * 고객센터 메시지를 누가 보냈는지 (계약서 §7-1, v1.22.0).
 *
 * FE 표시가 셋 다 다르다 — USER 는 내 말풍선, AI 는 "AI 상담사", OPERATOR 는
 * "킥오프 고객센터". <b>AI 와 OPERATOR 를 합치면 안 된다.</b> 사용자가 사람과 이야기하는
 * 줄 알고 환불·제재를 요구하는데 답한 쪽이 AI 인 상황이 생긴다.
 */
public enum SupportSender {
    USER,
    AI,
    OPERATOR
}
