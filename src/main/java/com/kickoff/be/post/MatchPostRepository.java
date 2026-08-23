package com.kickoff.be.post;

import com.kickoff.be.team.SkillLevel;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchPostRepository extends JpaRepository<MatchPost, Long> {

    /**
     * 목록 필터 (계약서 §5). 파라미터가 null 이면 그 조건은 건너뛴다.
     * 카드에 TeamSummary 가 박히므로 team 을 같이 끌고 와 N+1 을 막는다.
     */
    @EntityGraph(attributePaths = "team")
    @Query("""
            select p from MatchPost p
            where (:region is null or lower(p.region) like lower(concat('%', :region, '%')))
              and (:fieldType is null or p.fieldType = :fieldType)
              and (:skillLevel is null or p.preferredSkillLevel = :skillLevel)
              and (:status is null or p.status = :status)
              and (:keyword is null
                   or lower(p.title) like lower(concat('%', :keyword, '%'))
                   or lower(p.content) like lower(concat('%', :keyword, '%')))
            """)
    Page<MatchPost> search(@Param("region") String region,
                           @Param("fieldType") FieldType fieldType,
                           @Param("skillLevel") SkillLevel skillLevel,
                           @Param("status") PostStatus status,
                           @Param("keyword") String keyword,
                           Pageable pageable);

    /** 상세 응답의 team 이 TeamResponse(ownerNickname 포함)라 owner 까지 필요하다. */
    @EntityGraph(attributePaths = {"team", "team.owner"})
    @Query("select p from MatchPost p where p.id = :id")
    Optional<MatchPost> findWithTeamAndOwnerById(@Param("id") Long id);

    @EntityGraph(attributePaths = "team")
    Page<MatchPost> findByTeamId(Long teamId, Pageable pageable);
}
