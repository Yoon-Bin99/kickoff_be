package com.kickoff.be.chatlink.repository;

import com.kickoff.be.chatlink.entity.ChatOutbox;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatOutboxRepository extends JpaRepository<ChatOutbox, Long> {

    /**
     * 보낼 차례가 된 이벤트 (계약서 §10 "오래된 순으로 최대 50개").
     *
     * <b>오래된 순 = id 순</b>이다. 같은 방에 대한 이벤트가 둘 이상 쌓였을 때 순서가
     * 뒤집히면 채팅 서버가 옛 상태를 나중에 받는다. created_at 이 아니라 id 로 세우는 건
     * 같은 트랜잭션에서 넣은 여러 행이 같은 시각을 받을 수 있기 때문이다.
     *
     * dead 행(채팅 서버가 못 받는다고 답한 것)은 빠진다 — 다시 보내 봐야 같은 답이 온다.
     */
    @Query("""
            select o from ChatOutbox o
            where o.deliveredAt is null and o.deadAt is null and o.nextAttemptAt <= :now
            order by o.id asc
            """)
    List<ChatOutbox> findDeliverable(@Param("now") OffsetDateTime now, Pageable pageable);

    /**
     * 너무 오래 못 보낸 PURGE (법률 점검 11절).
     *
     * <b>PURGE 만 본다.</b> ROOM_INVALIDATED 가 늦는 것은 채팅 서버의 15초 캐시가 메워 주지만,
     * PURGE 가 늦는 것은 <b>탈퇴한 사람의 대화가 아직 안 지워졌다</b>는 뜻이다 — 개인정보
     * 보호법 제21조가 "지체 없이" 파기하라고 한 그 자리다.
     *
     * dead 행도 포함한다. 포기한 행이야말로 사람이 손을 대야 하는 것이고, 조용해지면
     * 아무도 모른다.
     */
    @Query("""
            select o from ChatOutbox o
            where o.deliveredAt is null
              and o.eventType = com.kickoff.be.chatlink.entity.ChatEventType.PURGE
              and o.createdAt < :cutoff
            order by o.id asc
            """)
    List<ChatOutbox> findOverduePurges(@Param("cutoff") OffsetDateTime cutoff);

    /**
     * 전달이 끝나고 보관 기간이 지난 행 (계약서 §10 "완료 후 7일").
     *
     * dead 행은 {@code deliveredAt} 이 비어 있어 여기 걸리지 않는다 — 의도된 것이다.
     * 원인을 조사하고 수동으로 조치할 때까지 남아 있어야 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatOutbox o where o.deliveredAt is not null and o.deliveredAt < :cutoff")
    int deleteDeliveredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
