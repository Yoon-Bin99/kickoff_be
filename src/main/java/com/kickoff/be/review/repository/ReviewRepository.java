package com.kickoff.be.review.repository;

import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.entity.Review;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 팀이 받은 리뷰 목록 — ReviewResponse 에 필요한 연관을 한 번에 끌고 온다. */
    @EntityGraph(attributePaths = {"request", "request.post", "reviewerTeam", "targetTeam"})
    Page<Review> findByTargetTeamId(Long targetTeamId, Pageable pageable);

    boolean existsByRequestIdAndReviewerTeamId(Long requestId, Long reviewerTeamId);

    long countByTargetTeamId(Long targetTeamId);

    /** 리뷰가 하나도 없으면 null 이 나온다 — ReviewStats 가 그대로 계약서의 null 로 바꾼다. */
    @Query("select avg(r.rating) from Review r where r.targetTeam.id = :teamId")
    Double averageRatingOf(@Param("teamId") Long teamId);

    /**
     * 신청 목록의 myReviewWritten 을 채우려고 한 번에 긁어온다.
     * 목록마다 신청 수만큼 exists 를 날리면 N+1 이다.
     */
    @Query("""
            select r.request.id from Review r
            where r.reviewerTeam.id = :teamId
              and r.request.id in :requestIds
            """)
    List<Long> findReviewedRequestIds(@Param("teamId") Long teamId,
                                      @Param("requestIds") Collection<Long> requestIds);

    default ReviewStats statsOf(Long teamId) {
        return new ReviewStats(countByTargetTeamId(teamId), averageRatingOf(teamId));
    }
}
