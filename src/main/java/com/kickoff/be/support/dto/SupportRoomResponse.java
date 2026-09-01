package com.kickoff.be.support.dto;

/** 운영자 문의함 목록 한 줄 (계약서 §7-1). */
public record SupportRoomResponse(
        Long userId,
        String nickname,
        SupportMessageResponse lastMessage
) {
}
