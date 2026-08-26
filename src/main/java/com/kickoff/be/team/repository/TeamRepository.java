package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.Team;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * 팀 찾기 (계약서 §4, v1.11.0). 이름·지역 부분 일치, 생성일 DESC(동률 id DESC).
     *
     * cast(:x as string) 는 없어도 될 것 같지만 필요하다. 파라미터가 null 일 때 H2 는
     * 그냥 넘어가지만 PostgreSQL 은 타입을 정하지 못해 lower(bytea) 로 해석하고 터진다 —
     * 목록 조회에서 실제로 당했던 것과 같은 함정이다(v1.5.0).
     *
     * createdAt 만으로 정렬하면 같은 밀리초에 만들어진 두 팀의 상대 순서가 조회마다 달라져
     * 페이징에서 같은 팀이 두 번 나오거나 빠진다. id 를 2차 키로 넣어 결정적으로 만든다.
     */
    @Query("""
            select t from Team t
            join fetch t.owner
            where (:keyword is null or lower(t.name) like lower(concat('%', cast(:keyword as string), '%')))
              and (:region is null or lower(t.region) like lower(concat('%', cast(:region as string), '%')))
            order by t.createdAt desc, t.id desc
            """)
    Page<Team> search(@Param("keyword") String keyword, @Param("region") String region,
                      Pageable pageable);

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
