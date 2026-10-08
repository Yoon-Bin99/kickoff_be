package com.kickoff.be.team.repository;

import com.kickoff.be.team.entity.Squad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SquadRepository extends JpaRepository<Squad, Long> {

    /**
     * 목록 — updatedAt 내림차순, 페이지 없음 (계약서 §4-4, 최대 30개).
     *
     * id 를 2차 키로 넣는다. 같은 트랜잭션에서 두 개를 만들면 updatedAt 이 같을 수 있는데
     * (감사 시각은 저장 시점이고 마이크로초 단위다), 그때 상대 순서가 조회마다 달라진다.
     *
     * 항목은 끌고 오지 않는다 — 목록 응답에 자리 내용이 없어서다(SquadSummary).
     */
    List<Squad> findByTeamIdOrderByUpdatedAtDescIdDesc(Long teamId);

    /**
     * 단건 — 항목까지 함께. 자리마다 조회하면 N+1 이다.
     *
     * <b>팀원은 여기서 함께 끌어오지 않는다.</b> {@code items.member} 를 EntityGraph 에
     * 넣으면 member 가 null 인 항목(게스트·빈 자리·지워진 팀원)까지 섞여 조인이 커지는데,
     * 자리가 최대 21개라 지연 로딩으로 두는 편이 단순하다. 서비스가 트랜잭션 안에서 읽는다.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Squad> findWithItemsByIdAndTeamId(Long squadId, Long teamId);

    long countByTeamId(Long teamId);

    /**
     * 팀 삭제·소유자 탈퇴 때의 삭제 쿼리는 여기 없다 —
     * {@code AccountDeletionRepository} 에 있다 (계약서 §4-4 cascade, §3-4).
     *
     * 탈퇴는 순서가 전부인 작업이라 쿼리를 한 곳에 모아 두는 것이 그 저장소의 규칙이다.
     * 여기에 하나만 따로 두면 다음 사람이 전체 순서를 읽을 수 없게 된다.
     */
}
