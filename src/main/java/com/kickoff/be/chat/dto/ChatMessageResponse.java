package com.kickoff.be.chat.dto;

import com.kickoff.be.chat.entity.ChatMessage;
import com.kickoff.be.chat.entity.ChatMessageType;
import java.time.OffsetDateTime;

/**
 * 메시지 한 건 (계약서 §6-1, v1.12.0 / type 은 v1.13.0).
 *
 * {@code senderTeamId} 는 SYSTEM 이면 null 이다 — FE 가 말풍선 대신 가운데 안내로 그린다.
 */
public record ChatMessageResponse(
        Long id,
        Long requestId,
        ChatMessageType type,
        Long senderTeamId,
        String content,
        OffsetDateTime createdAt
) {

    public static ChatMessageResponse of(ChatMessage message) {
        return new ChatMessageResponse(message.getId(), message.getRequestId(),
                message.getType(), message.getSenderTeamId(), message.getContent(),
                message.getCreatedAt());
    }
}
