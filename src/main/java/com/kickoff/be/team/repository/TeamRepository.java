package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.Team;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    /** ownerNickname 을 채워야 하는 응답용 — owner 를 같이 끌고 온다. */
    @EntityGraph(attributePaths = "owner")
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findWithOwnerById(Long id);

    @EntityGraph(attributePaths = "owner")
    @Query("select t from Team t where t.owner.id = :ownerId")
    Optional<Team> findWithOwnerByOwnerId(Long ownerId);
}
