package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.TeamMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    /**
     * 등번호 오름차순, 등번호가 없는 팀원은 뒤에 이름순 (계약서 §4-1).
     *
     * 정렬을 메서드 이름으로 표현하지 않고 JPQL 로 쓴 이유는 "null 을 뒤로"가 DB 마다 기본값이
     * 다르기 때문이다. PostgreSQL 은 오름차순에서 null 을 뒤에 두지만 H2 는 앞에 둔다 —
     * 명시하지 않으면 개발과 운영에서 목록 순서가 갈린다.
     *
     * <b>이름에 {@code sortkey()} 를 씌운 것도 같은 성격의 문제다.</b> 한글 정렬이 서버
     * libc 에 따라 달라져서, 이걸 안 씌우면 명단 순서가 개발과 운영에서 갈린다 — 구장
     * 목록에서 실제로 당했다 ({@link com.kickoff.be.config.SortKeyFunctionContributor}).
     * 여기는 아직 사고가 난 적 없지만 한글 텍스트로 정렬하는 나머지 한 자리라 같이 막는다.
     */
    @Query("""
            select m from TeamMember m
            where m.team.id = :teamId
            order by case when m.backNumber is null then 1 else 0 end, m.backNumber,
                     sortkey(m.name)
            """)
    List<TeamMember> findByTeamIdOrdered(Long teamId);

    Optional<TeamMember> findByIdAndTeamId(Long id, Long teamId);

    long countByTeamId(Long teamId);

    /** 이 사람이 이 팀 명단에 계정으로 올라 있는지 — MEMBER 판정에 쓴다 (계약서 §4-3). */
    boolean existsByTeamIdAndUser_Id(Long teamId, Long userId);

    Optional<TeamMember> findByTeamIdAndUser_Id(Long teamId, Long userId);

    /** 내가 소속된 팀들 — /users/me/teams 의 MEMBER 부분. 가입순(= 등재순)이다. */
    @EntityGraph(attributePaths = "team")
    List<TeamMember> findByUser_IdOrderByIdAsc(Long userId);

    /** 닉네임이 바뀌었을 때 따라가야 할 명단 항목들 (계약서 §4-3). */
    List<TeamMember> findByUser_Id(Long userId);
}
