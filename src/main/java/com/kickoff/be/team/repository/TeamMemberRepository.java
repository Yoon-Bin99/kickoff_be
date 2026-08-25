package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /**
     * 등번호 오름차순, 등번호가 없는 팀원은 뒤에 이름순 (계약서 §4-1).
     *
     * 정렬을 메서드 이름으로 표현하지 않고 JPQL 로 쓴 이유는 "null 을 뒤로"가 DB 마다 기본값이
     * 다르기 때문이다. PostgreSQL 은 오름차순에서 null 을 뒤에 두지만 H2 는 앞에 둔다 —
     * 명시하지 않으면 개발과 운영에서 목록 순서가 갈린다.
     */
    @Query("""
            select m from TeamMember m
            where m.team.id = :teamId
            order by case when m.backNumber is null then 1 else 0 end, m.backNumber, m.name
            """)
    List<TeamMember> findByTeamIdOrdered(Long teamId);

    Optional<TeamMember> findByIdAndTeamId(Long id, Long teamId);

    long countByTeamId(Long teamId);
}
