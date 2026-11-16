package com.kickoff.be.block.repository;

import com.kickoff.be.block.entity.TeamBlock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamBlockRepository extends JpaRepository<TeamBlock, Long> {

    @EntityGraph(attributePaths = {"blockedTeam", "blockedTeam.owner"})
    List<TeamBlock> findByBlockerIdOrderByCreatedAtDesc(Long blockerId);

    Optional<TeamBlock> findByBlockerIdAndBlockedTeamId(Long blockerId, Long blockedTeamId);

    boolean existsByBlockerIdAndBlockedTeamId(Long blockerId, Long blockedTeamId);

    /** 이 계정이 차단한 팀들. 목록 조회에서 그 팀의 글·리뷰를 빼는 데 쓴다. */
    @Query("select b.blockedTeam.id from TeamBlock b where b.blocker.id = :blockerId")
    List<Long> findBlockedTeamIdsBy(@Param("blockerId") Long blockerId);

    /**
     * 두 팀 사이에 차단이 있는지 (계약서 §7-2 — <b>감독의 차단만</b> 센다).
     *
     * 어느 방향이든 하나면 둘 다 막힌다. 차단한 쪽만 막으면 상대는 계속 신청을 보낼 수
     * 있고, 받는 쪽 화면에는 그 신청이 보이지 않는 이상한 상태가 된다.
     */
    @Query("""
            select count(b) > 0 from TeamBlock b
            where (b.blocker.id = :ownerA and b.blockedTeam.id = :teamB)
               or (b.blocker.id = :ownerB and b.blockedTeam.id = :teamA)
            """)
    boolean existsBetween(@Param("ownerA") Long ownerA, @Param("teamA") Long teamA,
                          @Param("ownerB") Long ownerB, @Param("teamB") Long teamB);

    /** 탈퇴하면 그 계정이 건 차단과 그 계정의 팀을 대상으로 한 차단이 함께 사라진다. */
    void deleteByBlockerId(Long blockerId);

    void deleteByBlockedTeamId(Long blockedTeamId);
}
