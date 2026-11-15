package com.kickoff.be.chatlink.service;

import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.dto.OverduePurge;
import com.kickoff.be.chatlink.repository.ChatOutboxRepository;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * chat_outbox 를 읽고 표시하는 일만 한다 (계약서 §10).
 *
 * <b>스케줄러와 따로 둔 이유는 트랜잭션 경계다.</b> HTTP 왕복은 트랜잭션 밖에서 일어나야
 * 하는데, 한 클래스 안에서 메서드를 나눠 봐야 {@code @Transactional} 은 자기 호출에
 * 걸리지 않는다. 빈을 나눠야 "읽기 한 번 → 보내기(트랜잭션 없음) → 표시 한 번"이 실제로
 * 그 경계대로 돈다.
 *
 * 표시가 건별로 작은 트랜잭션인 것도 의도다. 50건을 한 트랜잭션에 묶으면 중간에 끊겼을 때
 * 이미 보낸 것까지 미전송으로 되돌아가, 채팅 서버가 같은 이벤트를 또 받는다(멱등이라
 * 틀리지는 않지만 쓸모없는 일을 시킨다).
 */
@Service
@RequiredArgsConstructor
public class ChatOutboxStore {

    private final ChatOutboxRepository repository;

    @Transactional(readOnly = true)
    public List<ChatOutboxMessage> deliverable(OffsetDateTime now, int limit) {
        return repository.findDeliverable(now, PageRequest.ofSize(limit)).stream()
                .map(ChatOutboxMessage::of)
                .toList();
    }

    @Transactional
    public void markDelivered(Long id, OffsetDateTime now) {
        repository.findById(id).ifPresent(outbox -> outbox.markDelivered(now));
    }

    @Transactional
    public void markFailed(Long id, OffsetDateTime now) {
        repository.findById(id).ifPresent(outbox -> outbox.markFailed(now));
    }

    /**
     * 채팅 서버가 거절했다 (계약서 §10). 몇 번 더 가 보고 그만둔다.
     *
     * @return 이번에 dead 가 됐으면 true. 호출자는 그때만 ERROR 를 남긴다 — 매번 남기면
     *         같은 행이 다섯 줄을 만든다
     */
    @Transactional
    public boolean markRejected(Long id, OffsetDateTime now) {
        return repository.findById(id)
                .map(outbox -> outbox.markRejected(now))
                .orElse(false);
    }

    /** 너무 오래 못 보낸 PURGE (법률 점검 11절). */
    @Transactional(readOnly = true)
    public List<OverduePurge> overduePurges(OffsetDateTime cutoff) {
        return repository.findOverduePurges(cutoff).stream().map(OverduePurge::of).toList();
    }

    @Transactional
    public int deleteDeliveredBefore(OffsetDateTime cutoff) {
        return repository.deleteDeliveredBefore(cutoff);
    }
}
