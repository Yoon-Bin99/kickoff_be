package com.kickoff.be.matchrequest;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    /**
     * 계약서의 requestCount — 아직 살아 있는 신청(PENDING/ACCEPTED)만 센다.
     * 글 목록에서 N+1 이 나지 않게 페이지 단위로 한 번에 집계한다.
     */
    @Query("""
            select new com.kickoff.be.matchrequest.PostRequestCount(r.post.id, count(r))
            from MatchRequest r
            where r.post.id in :postIds
              and r.status in (com.kickoff.be.matchrequest.RequestStatus.PENDING,
                               com.kickoff.be.matchrequest.RequestStatus.ACCEPTED)
            group by r.post.id
            """)
    List<PostRequestCount> countActiveByPostIds(@Param("postIds") Collection<Long> postIds);
}
