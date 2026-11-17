package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.MatchPost;
import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * 날씨를 받아 올 지역 (계약서 §8-3).
     *
     * <b>표 전체가 아니라 경기가 잡힌 곳만</b> 받는다 — 전국 250여 곳을 매번 받으면
     * 하루 한도를 금세 쓰고, 그중 대부분은 아무도 보지 않는 예보다.
     *
     * 글이 삭제되면 그 지역은 저절로 빠진다(모집글은 실제로 지운다).
     */
    @Query("select distinct p.region from MatchPost p "
            + "where p.matchAt > :from and p.matchAt < :to and p.region is not null")
    List<String> findRegionsWithUpcomingMatch(@Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to);
}
