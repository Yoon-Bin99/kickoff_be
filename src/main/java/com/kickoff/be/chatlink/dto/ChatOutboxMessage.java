package com.kickoff.be.chatlink.dto;

import com.kickoff.be.chatlink.entity.ChatEventType;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import java.util.List;

/**
 * 보낼 이벤트 한 건을 트랜잭션 밖으로 들고 나가는 값 (계약서 §10).
 *
 * 엔티티를 그대로 넘기지 않는 이유가 있다. 전달은 <b>트랜잭션 밖에서</b> 일어나야 한다 —
 * HTTP 왕복 동안 DB 커넥션을 쥐고 있으면 채팅 서버가 느린 날 커넥션 풀이 말라 BE 전체가
 * 멈춘다. 50건을 한 바퀴에 보내므로 최악을 곱하면 금방 커진다.
 */
public record ChatOutboxMessage(Long eventId, ChatEventType type, List<Long> requestIds,
                                List<Long> teamIds) {

    public static ChatOutboxMessage of(ChatOutbox outbox) {
        return new ChatOutboxMessage(outbox.getId(), outbox.getEventType(),
                outbox.requestIdList(), outbox.teamIdList());
    }
}
