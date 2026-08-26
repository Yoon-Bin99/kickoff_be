package com.kickoff.be.team.repository;

import com.kickoff.be.team.dto.RecordSummary;
import com.kickoff.be.team.entity.TeamRecord;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeamRecordRepository extends JpaRepository<TeamRecord, Long> {

    /** playedOn DESC, 같은 날이면 id DESC (계약서 §4-1). */
    @Query("select r from TeamRecord r where r.team.id = :teamId order by r.playedOn desc, r.id desc")
    Page<TeamRecord> findByTeamIdOrdered(Long teamId, Pageable pageable);

    Optional<TeamRecord> findByIdAndTeamId(Long id, Long teamId);

    /**
     * 같은 팀이 같은 매칭으로 이미 기록했는지 (계약서 §4-1, v1.10.0).
     *
     * 밑줄로 연관을 명시한다. 엔티티에 편의용 {@code getRequestId()} 가 있어서, 밑줄이 없으면
     * Spring Data 가 그걸 영속 속성으로 착각해 {@code t.requestId} 로 쿼리를 만들고 기동
     * 후 첫 호출에서 UnknownPathException 으로 터진다.
     */
    boolean existsByRequest_IdAndTeamId(Long requestId, Long teamId);

    /**
     * 신청 목록의 myRecordWritten 을 한 번에 채운다. 신청마다 exists 를 날리면 N+1 이다 —
     * 리뷰의 findReviewedRequestIds 와 같은 방식이다.
     */
    @Query("""
            select r.request.id from TeamRecord r
            where r.team.id = :teamId
              and r.request.id in :requestIds
            """)
    List<Long> findRecordedRequestIds(@Param("teamId") Long teamId,
                                      @Param("requestIds") Collection<Long> requestIds);

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
