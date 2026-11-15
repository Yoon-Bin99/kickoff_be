package com.kickoff.be.chatlink.dto;

import com.kickoff.be.chatlink.entity.ChatOutbox;
import java.time.OffsetDateTime;

/**
 * 너무 오래 못 보낸 PURGE 한 건 (법률 점검 11절).
 *
 * <b>id 목록을 담지 않는다.</b> 이 값은 ERROR 로그가 되는데, 거기에 requestId 들을 적으면
 * 로그가 "누가 언제 탈퇴했고 어떤 매칭을 했는가"를 들고 있게 된다 — 지우라는 경보가
 * 개인정보를 또 한 벌 만드는 셈이다. 무엇을 지워야 하는지는 {@code chat_outbox} 의 그 행이
 * 들고 있으므로, 로그에는 <b>그 행을 찾을 수 있는 번호</b>만 있으면 된다.
 */
public record OverduePurge(Long eventId, OffsetDateTime createdAt, int attempts, boolean dead) {

    public static OverduePurge of(ChatOutbox outbox) {
        return new OverduePurge(outbox.getId(), outbox.getCreatedAt(), outbox.getAttempts(),
                outbox.isDead());
    }
}
