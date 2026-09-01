package com.kickoff.be.support.repository;

import com.kickoff.be.support.entity.SupportMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    /** 폴링 — 커서 이후만, 오래된 것부터 (계약서 §7-1, §6-1과 같은 규칙). */
    List<SupportMessage> findByUser_IdAndIdGreaterThanOrderByIdAsc(Long userId, Long after,
                                                                   Pageable pageable);

    /**
     * 첫 로드 — <b>최신</b> limit 개. 내림차순으로 가져와 호출자가 뒤집는다.
     *
     * 오름차순으로 limit 를 걸면 방의 가장 오래된 것부터 나온다. 문의가 길어질수록
     * 화면에 옛날 이야기만 뜨는데, 에러가 아니라 엉뚱한 화면이라 눈으로 봐야만 안다.
     */
    List<SupportMessage> findByUser_IdAndIdGreaterThanOrderByIdDesc(Long userId, Long after,
                                                                    Pageable pageable);

    /**
     * 운영자 문의함 목록 — 방마다 마지막 메시지 id (계약서 §7-1).
     *
     * <b>메시지가 있는 방만</b> 나온다. 문의가 시작돼야 방이 의미를 가지므로, 빈 방(첫
     * 조회로 만들어진 방)은 여기서 자연히 빠진다 — 목록에서 걸러낼 필요가 없다.
     */
    @Query("""
            select m.user.id, max(m.id) from SupportMessage m
            group by m.user.id
            """)
    List<Object[]> findLastIdPerUser();

    /** 위에서 고른 마지막 메시지들을 한 번에. 목록에 닉네임을 찍어야 해서 user 까지. */
    @Query("select m from SupportMessage m join fetch m.user where m.id in :ids")
    List<SupportMessage> findAllWithUserByIdIn(@Param("ids") Collection<Long> ids);

    /** 분당 호출 한도 계산용 — 이 사용자가 최근에 보낸 메시지 수 (계약서 §7-1). */
    @Query("""
            select count(m) from SupportMessage m
            where m.user.id = :userId
              and m.sender = com.kickoff.be.support.entity.SupportSender.USER
              and m.createdAt >= :since
            """)
    long countUserMessagesSince(@Param("userId") Long userId,
                                @Param("since") java.time.OffsetDateTime since);
}
