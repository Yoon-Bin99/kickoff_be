package com.kickoff.be.chat.dto;

import java.util.List;

/**
 * 채팅 조회 응답 (계약서 §6-1, v1.12.0).
 *
 * chatOpen 을 함께 내려보내는 이유는 FE 가 <b>클라이언트 시계로 판정하면 안 되기</b>
 * 때문이다. 기기 시계가 어긋나 있으면 아직 열린 방의 입력창이 잠기거나, 이미 닫힌 방에
 * 글을 쓰려다 409 를 맞는다. 판정은 서버 시계로 한 번만 한다.
 */
public record ChatResponse(List<ChatMessageResponse> messages, boolean chatOpen) {
}
