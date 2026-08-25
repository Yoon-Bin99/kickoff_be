package com.kickoff.be.team.repository;

import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.entity.TeamRecord;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TeamRecordRepository extends JpaRepository<TeamRecord, Long> {

    /** playedOn DESC, 같은 날이면 id DESC (계약서 §4-1). */
    @Query("select r from TeamRecord r where r.team.id = :teamId order by r.playedOn desc, r.id desc")
    Page<TeamRecord> findByTeamIdOrdered(Long teamId, Pageable pageable);

    Optional<TeamRecord> findByIdAndTeamId(Long id, Long teamId);

    /**
     * 전적 요약 (계약서 §4-1). result 컬럼이 없으므로 스코어를 비교해 센다 —
     * 저장된 값이 아니라 계산이라 기록과 요약이 어긋날 수가 없다.
     *
     * 기록이 하나도 없으면 sum 이 전부 null 인 행 하나가 나온다. 0 으로 바꾸는 건
     * RecordSummary 가 맡는다.
     */
    @Query("""
            select new com.kickoff.be.team.dto.RecordSummary(
                sum(case when r.ourScore > r.opponentScore then 1L else 0L end),
                sum(case when r.ourScore = r.opponentScore then 1L else 0L end),
                sum(case when r.ourScore < r.opponentScore then 1L else 0L end))
            from TeamRecord r where r.team.id = :teamId
            """)
    RecordSummary summaryOf(Long teamId);
}
