package com.kickoff.be.support.dto;

import com.kickoff.be.support.entity.SupportMessage;
import com.kickoff.be.support.entity.SupportSender;
import java.time.OffsetDateTime;

/** 계약서 §7-1. 방 주인이 누구인지는 싣지 않는다 — 사용자는 자기 방만 본다. */
public record SupportMessageResponse(
        Long id,
        SupportSender sender,
        String content,
        OffsetDateTime createdAt
) {

    public static SupportMessageResponse of(SupportMessage message) {
        return new SupportMessageResponse(message.getId(), message.getSender(),
                message.getContent(), message.getCreatedAt());
    }
}
