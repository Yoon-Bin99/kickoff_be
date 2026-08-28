package com.kickoff.be.chat.entity;

/**
 * 메시지 종류 (계약서 §6-1, v1.13.0).
 *
 * TEXT 는 사람이 보낸 말풍선, SYSTEM 은 서버가 끼워 넣는 안내 줄이다. 둘을 한 테이블에
 * 두는 이유는 <b>순서</b> 때문이다 — "언제 나갔는가"는 대화 흐름 안의 위치가 곧 의미라서,
 * 따로 저장하면 FE 가 두 목록을 시각으로 병합해야 하고 같은 초에 걸리면 순서가 흔들린다.
 * 같은 시퀀스를 쓰면 id 하나로 정렬이 끝난다.
 *
 * SYSTEM 은 보낸 팀이 없다 ({@code senderTeamId: null}). 나가기 안내를 "나간 팀이 보낸
 * 말"로 적으면 FE 가 그 팀 말풍선으로 그려 버린다.
 */
public enum ChatMessageType {
    TEXT,
    SYSTEM
}
