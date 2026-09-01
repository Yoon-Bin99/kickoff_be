package com.kickoff.be.support.dto;

import java.util.List;

/**
 * 계약서 §7-1. 매칭 채팅과 달리 {@code chatOpen} 이 없다 — 문의방은 항상 열려 있다.
 */
public record SupportChatResponse(List<SupportMessageResponse> messages) {
}
