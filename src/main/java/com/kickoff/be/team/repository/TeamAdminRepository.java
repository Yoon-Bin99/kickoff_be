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

    /** 내가 관리하는 팀들 (계약서 §4-2, v1.9.1). 임명순. 팀 카드를 만들 것이라 team 도 함께. */
    @EntityGraph(attributePaths = "team")
    List<TeamAdmin> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByTeamIdAndUserId(Long teamId, Long userId);

    Optional<TeamAdmin> findByTeamIdAndUserId(Long teamId, Long userId);

    long countByTeamId(Long teamId);
}
