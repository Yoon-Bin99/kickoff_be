package com.kickoff.be.support.service;

/**
 * AI 답변이 필요하다 (계약서 §7-1, v1.22.0).
 *
 * 사용자 메시지가 <b>커밋된 뒤에</b> 처리된다. 전송 트랜잭션 안에서 제공자를 부르면
 * 왕복(최대 20초) 동안 DB 커넥션이 붙잡히고, 롤백되면 있지도 않은 메시지에 답이 달린다.
 */
record AiReplyRequestedEvent(Long userId) {
}
