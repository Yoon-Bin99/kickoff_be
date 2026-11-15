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
     */
    @Query("""
            select o from ChatOutbox o
            where o.deliveredAt is null and o.nextAttemptAt <= :now
            order by o.id asc
            """)
    List<ChatOutbox> findDeliverable(@Param("now") OffsetDateTime now, Pageable pageable);

    /** 전달이 끝나고 보관 기간이 지난 행 (계약서 §10 "완료 후 7일"). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatOutbox o where o.deliveredAt is not null and o.deliveredAt < :cutoff")
    int deleteDeliveredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
