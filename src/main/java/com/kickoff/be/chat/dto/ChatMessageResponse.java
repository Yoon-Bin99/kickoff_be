package com.kickoff.be.chat.dto;

import com.kickoff.be.chat.entity.ChatMessage;
import java.time.OffsetDateTime;

/** 메시지 한 건 (계약서 §6-1, v1.12.0). */
public record ChatMessageResponse(
        Long id,
        Long requestId,
        Long senderTeamId,
        String content,
        OffsetDateTime createdAt
) {

    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRequestId(),
                message.getSenderTeamId(), message.getContent(), message.getCreatedAt());
    }
}
