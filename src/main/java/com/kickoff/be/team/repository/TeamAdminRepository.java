package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.TeamAdmin;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamAdminRepository extends JpaRepository<TeamAdmin, Long> {

    /** 임명순 (계약서 §4-2). 닉네임을 함께 내려보내므로 user 를 같이 가져온다. */
    @EntityGraph(attributePaths = "user")
    List<TeamAdmin> findByTeamIdOrderByIdAsc(Long teamId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamAdmin> findByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamId(Long teamId);
}
