package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.JoinStatus;
import com.kickoff.be.team.entity.TeamJoinRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Long> {

    /** 대기 중인 신청 목록 — 오래된 순. 먼저 온 신청부터 처리한다 (계약서 §4-3). */
    @EntityGraph(attributePaths = "user")
    List<TeamJoinRequest> findByTeamIdAndStatusOrderByIdAsc(Long teamId, JoinStatus status);

    Optional<TeamJoinRequest> findByIdAndTeamId(Long id, Long teamId);

    /** 내 대기 중인 신청 — 취소와 myJoinStatus 판정에 쓴다. */
    Optional<TeamJoinRequest> findByTeamIdAndUserIdAndStatus(Long teamId, Long userId,
                                                            JoinStatus status);

    boolean existsByTeamIdAndUserIdAndStatus(Long teamId, Long userId, JoinStatus status);
}
