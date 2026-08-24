package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.FieldType;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.team.entity.SkillLevel;
import java.time.OffsetDateTime;
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
     *
     * 지난 경기는 status 와 무관하게 빠진다 — 매칭 대상이 아니라서다.
     * 내 글 목록(findByTeamId)에는 이 조건을 걸지 않는다. 그쪽은 기록이니까.
     *
     * region 과 keyword 를 <b>cast(... as string)</b> 으로 감싼 이유가 있다. 이 둘은 보통 null 인데,
     * PostgreSQL 은 타입 정보 없는 null 파라미터를 bytea 로 추론해서 lower(bytea) 를 찾다가
     * 실패한다 (function lower(bytea) does not exist). H2 는 이걸 그냥 넘어가기 때문에
     * 실제 PostgreSQL 로 띄워보기 전에는 드러나지 않았다. 캐스팅이 타입을 못 박아준다.
     */
    @EntityGraph(attributePaths = "team")
    @Query("""
            select p from MatchPost p
            where p.matchAt >= :now
              and (:region is null or lower(p.region) like lower(concat('%', cast(:region as string), '%')))
              and (:fieldType is null or p.fieldType = :fieldType)
              and (:skillLevel is null or p.preferredSkillLevel = :skillLevel)
              and (:status is null or p.status = :status)
              and (:keyword is null
                   or lower(p.title) like lower(concat('%', cast(:keyword as string), '%'))
                   or lower(p.content) like lower(concat('%', cast(:keyword as string), '%')))
            """)
    Page<MatchPost> search(@Param("now") OffsetDateTime now,
                           @Param("region") String region,
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
