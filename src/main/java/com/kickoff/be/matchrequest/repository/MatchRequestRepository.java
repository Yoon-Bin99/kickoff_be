package com.kickoff.be.matchrequest.repository;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {

    /**
     * RequestResponse 하나를 만드는 데 필요한 연관을 전부 끌고 온다.
     * postStatus·applicantTeam 은 물론, contact 판정에 양쪽 팀의 owner 까지 쓴다.
     */
    String DETAIL_GRAPH = "MatchRequest.detail";

    @EntityGraph(attributePaths = {"post", "post.team", "post.team.owner",
            "applicantTeam", "applicantTeam.owner"})
    @Query("select r from MatchRequest r where r.id = :id")
    Optional<MatchRequest> findDetailById(@Param("id") Long id);

    /** 한 글에 온 신청 — PENDING 먼저, 그다음 최신순 (계약서 §6). */
    @EntityGraph(attributePaths = {"post", "post.team", "post.team.owner",
            "applicantTeam", "applicantTeam.owner"})
    @Query("""
            select r from MatchRequest r
            where r.post.id = :postId
            order by case when r.status = com.kickoff.be.matchrequest.entity.RequestStatus.PENDING
                          then 0 else 1 end,
                     r.createdAt desc
            """)
    List<MatchRequest> findByPostIdOrdered(@Param("postId") Long postId);

    /** 내 팀이 쓴 모든 글에 온 신청을 한 번에 (FE 매칭관리 탭의 반복 호출 제거용). */
    @EntityGraph(attributePaths = {"post", "post.team", "post.team.owner",
            "applicantTeam", "applicantTeam.owner"})
    @Query("""
            select r from MatchRequest r
            where r.post.team.id = :teamId
            order by case when r.status = com.kickoff.be.matchrequest.entity.RequestStatus.PENDING
                          then 0 else 1 end,
                     r.createdAt desc
            """)
    List<MatchRequest> findReceivedByTeamId(@Param("teamId") Long teamId);

    @EntityGraph(attributePaths = {"post", "post.team", "post.team.owner",
            "applicantTeam", "applicantTeam.owner"})
    @Query("select r from MatchRequest r where r.applicantTeam.id = :teamId order by r.createdAt desc")
    List<MatchRequest> findSentByTeamId(@Param("teamId") Long teamId);

    /** 글당 ACCEPTED 는 최대 하나 — 수락되면 글이 MATCHED 가 되어 더 수락할 수 없다. */
    @EntityGraph(attributePaths = {"applicantTeam", "applicantTeam.owner"})
    @Query("""
            select r from MatchRequest r
            where r.post.id = :postId
              and r.status = com.kickoff.be.matchrequest.entity.RequestStatus.ACCEPTED
            """)
    Optional<MatchRequest> findAcceptedByPostId(@Param("postId") Long postId);

    /**
     * 수락 시 같은 글의 나머지 PENDING 을 한꺼번에 거절하려고 먼저 긁어온다.
     * 자동 거절된 팀에게도 알림이 나가야 해서(계약서 §8) 소유자까지 같이 끌고 온다.
     */
    @EntityGraph(attributePaths = {"applicantTeam", "applicantTeam.owner"})
    List<MatchRequest> findByPostIdAndStatusAndIdNot(Long postId, RequestStatus status, Long id);

    boolean existsByPostIdAndApplicantTeamIdAndStatusIn(Long postId, Long applicantTeamId,
                                                        Collection<RequestStatus> statuses);

    /** myRequestStatus 용 — 재신청 이력이 있을 수 있어 가장 최근 것을 본다. */
    Optional<MatchRequest> findFirstByPostIdAndApplicantTeamIdOrderByIdDesc(Long postId,
                                                                            Long applicantTeamId);

    /**
     * 계약서의 requestCount — 아직 살아 있는 신청(PENDING/ACCEPTED)만 센다.
     * 글 목록에서 N+1 이 나지 않게 페이지 단위로 한 번에 집계한다.
     */
    @Query("""
            select new com.kickoff.be.matchrequest.repository.PostRequestCount(r.post.id, count(r))
            from MatchRequest r
            where r.post.id in :postIds
              and r.status in (com.kickoff.be.matchrequest.entity.RequestStatus.PENDING,
                               com.kickoff.be.matchrequest.entity.RequestStatus.ACCEPTED)
            group by r.post.id
            """)
    List<PostRequestCount> countActiveByPostIds(@Param("postIds") Collection<Long> postIds);
}
