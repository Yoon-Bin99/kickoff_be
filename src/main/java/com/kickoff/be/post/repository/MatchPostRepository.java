package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.MatchPost;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPostRepository
        extends JpaRepository<MatchPost, Long>, MatchPostRepositoryCustom {

    /** 상세 응답의 team 이 TeamResponse(ownerNickname 포함)라 owner 까지 필요하다. */
    @EntityGraph(attributePaths = {"team", "team.owner"})
    @Query("select p from MatchPost p where p.id = :id")
    Optional<MatchPost> findWithTeamAndOwnerById(@Param("id") Long id);

    @EntityGraph(attributePaths = "team")
    Page<MatchPost> findByTeamId(Long teamId, Pageable pageable);
}
