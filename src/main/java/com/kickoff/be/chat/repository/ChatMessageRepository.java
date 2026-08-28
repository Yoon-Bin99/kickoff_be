package com.kickoff.be.chat.repository;

import com.kickoff.be.chat.entity.ChatMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * 폴링 — 커서 이후만, 오래된 것부터 (계약서 §6-1).
     *
     * v1.13.0 부터 커서는 FE 의 {@code after} 와 나가기 워터마크 중 <b>큰 쪽</b>이다.
     * 걸러내기를 여기서 끝내야 계약서가 약속한 "FE 가 거를 필요 없음"이 지켜진다.
     */
    List<ChatMessage> findByRequest_IdAndIdGreaterThanOrderByIdAsc(Long requestId, Long after,
                                                                  Pageable pageable);

    /**
     * 첫 로드 — <b>최신</b> limit 개. 내림차순으로 가져와 호출자가 뒤집는다.
     *
     * 오름차순으로 limit 를 걸면 방의 <b>가장 오래된</b> 것부터 나온다. 대화가 길어질수록
     * 화면에 옛날 이야기만 뜨는데, 에러가 아니라 그냥 엉뚱한 화면이라 눈으로 봐야만 안다.
     */
    List<ChatMessage> findByRequest_IdAndIdGreaterThanOrderByIdDesc(Long requestId, Long after,
                                                                    Pageable pageable);

    /** 나가기 워터마크용 — 지금 이 방의 마지막 메시지 id. 빈 방이면 비어 있다. */
    @Query("select max(m.id) from ChatMessage m where m.request.id = :requestId")
    Optional<Long> findLastIdOf(@Param("requestId") Long requestId);

    /**
     * 방 목록용 — 방마다 마지막 메시지 id 를 한 번에 (계약서 §6-1 정렬·lastMessage).
     *
     * 워터마크를 여기서 반영하지 않아도 되는 게 요령이다. 나가기는 <b>앞쪽을 자르는</b>
     * 것뿐이라, 방 전체의 최대 id 가 워터마크보다 크면 그게 곧 내게 보이는 마지막
     * 메시지이고, 작거나 같으면 내게 보이는 메시지가 아예 없다. 방마다 조건이 다른
     * 집계를 SQL 하나로 짜지 않아도 되고, 목록 조회가 두 쿼리로 끝난다.
     */
    @Query("""
            select m.request.id, max(m.id) from ChatMessage m
            where m.request.id in :requestIds
            group by m.request.id
            """)
    List<Object[]> findLastIdsOf(@Param("requestIds") Collection<Long> requestIds);

    /** 위에서 고른 마지막 메시지들을 한 번에. 말풍선 주인을 찍어야 해서 senderTeam 까지. */
    @Query("select m from ChatMessage m left join fetch m.senderTeam where m.id in :ids")
    List<ChatMessage> findAllWithSenderByIdIn(@Param("ids") Collection<Long> ids);
}
