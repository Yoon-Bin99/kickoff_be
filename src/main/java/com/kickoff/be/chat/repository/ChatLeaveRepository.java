package com.kickoff.be.chat.repository;

import com.kickoff.be.chat.entity.ChatLeave;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatLeaveRepository extends JpaRepository<ChatLeave, Long> {

    /**
     * 밑줄 경로를 쓰는 이유는 엔티티에 {@code getRequestId()} 편의 메서드가 있어서다.
     * {@code findByRequestIdAndTeamId} 로 적으면 Spring Data 가 그 메서드를 속성으로 오해해
     * UnknownPathException 을 낸다 (ChatMessageRepository 에서 이미 당했다).
     */
    Optional<ChatLeave> findByRequest_IdAndTeam_Id(Long requestId, Long teamId);

    /** 방 목록용 — 내 팀들의 나가기 상태를 한 번에. 방마다 조회하면 목록에서 N+1 이 된다. */
    List<ChatLeave> findByTeam_IdIn(Collection<Long> teamIds);
}
