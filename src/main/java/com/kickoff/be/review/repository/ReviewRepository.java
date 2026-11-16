package com.kickoff.be.review.repository;

import com.kickoff.be.review.dto.ReviewStats;
import com.kickoff.be.review.entity.Review;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /**
     * 집계에서 <b>가려진 리뷰는 뺀다</b> (계약서 §7-2, v1.35.0).
     *
     * 목록에서는 자리 표시로 남기지만 평점에는 섞지 않는다 — 신고로 가려진 별점이 팀
     * 평점을 끌어내린 채로 남으면, 조치가 오히려 제재가 된다.
     *
     * 기한은 쿼리에서 본다(목록 조회와 같은 이유 — 배치로 비우면 늦게 풀린다).
     */
    String NOT_MODERATED = """
            (r.moderation.status is null
             or (r.moderation.status = com.kickoff.be.common.ModerationStatus.HIDDEN
                 and r.moderation.until < :now))
            """;

    @Query("select count(r) from Review r where r.targetTeam.id = :teamId and " + NOT_MODERATED)
    long countVisibleOf(@Param("teamId") Long teamId, @Param("now") OffsetDateTime now);

    /** 리뷰가 하나도 없으면 null 이 나온다 — ReviewStats 가 그대로 계약서의 null 로 바꾼다. */
    @Query("select avg(r.rating) from Review r where r.targetTeam.id = :teamId and "
            + NOT_MODERATED)
    Double averageRatingOf(@Param("teamId") Long teamId, @Param("now") OffsetDateTime now);

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

    /**
     * 여러 팀의 평점을 한 번에 (계약서 §2, v1.10.0).
     *
     * 목록 카드마다 statsOf 를 부르면 페이지 크기만큼 쿼리가 늘어난다 — 계약서가 배치를
     * 못박은 이유다. 리뷰가 하나도 없는 팀은 결과에 아예 없으므로, 호출자는 없는 팀을
     * EMPTY 로 채워야 한다.
     */
    @Query("""
            select new com.kickoff.be.review.repository.TeamReviewStats(
                r.targetTeam.id, count(r), avg(r.rating))
            from Review r
            where r.targetTeam.id in :teamIds and """ + NOT_MODERATED + """
            group by r.targetTeam.id
            """)
    List<TeamReviewStats> statsOfTeams(@Param("teamIds") Collection<Long> teamIds,
                                       @Param("now") OffsetDateTime now);

    /**
     * "지금" 은 여기서 만든다 — 호출자가 스무 곳이 넘고, 전부 같은 뜻의 now 를 들고 다니게
     * 하면 빠뜨리는 자리가 생긴다. 가려졌는지는 그리는 순간의 질문이라 몇 밀리초 차이로
     * 답이 갈릴 자리도 아니다.
     */
    default ReviewStats statsOf(Long teamId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new ReviewStats(countVisibleOf(teamId, now), averageRatingOf(teamId, now));
    }

    /**
     * 팀 id → 평점 맵. 리뷰가 없는 팀은 {@link ReviewStats#EMPTY} 로 채워 돌려준다 —
     * 호출자가 getOrDefault 를 잊어도 null 이 새지 않게 한다.
     */
    default Map<Long, ReviewStats> statsMapOf(Collection<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ReviewStats> found = statsOfTeams(teamIds, OffsetDateTime.now()).stream()
                .collect(Collectors.toMap(TeamReviewStats::teamId,
                        s -> new ReviewStats(s.count(), s.average())));
        return teamIds.stream().distinct()
                .collect(Collectors.toMap(id -> id,
                        id -> found.getOrDefault(id, ReviewStats.EMPTY)));
    }
}
