package com.kickoff.be.chat.repository;

import com.kickoff.be.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 폴링 — 커서 이후만, 오래된 것부터 (계약서 §6-1). */
    List<ChatMessage> findByRequest_IdAndIdGreaterThanOrderByIdAsc(Long requestId, Long after,
                                                                  Pageable pageable);

    /**
     * 첫 로드 — <b>최신</b> limit 개. 내림차순으로 가져와 호출자가 뒤집는다.
     *
     * 오름차순으로 limit 를 걸면 방의 <b>가장 오래된</b> 것부터 나온다. 대화가 길어질수록
     * 화면에 옛날 이야기만 뜨는데, 에러가 아니라 그냥 엉뚱한 화면이라 눈으로 봐야만 안다.
     */
    List<ChatMessage> findByRequest_IdOrderByIdDesc(Long requestId, Pageable pageable);
}
