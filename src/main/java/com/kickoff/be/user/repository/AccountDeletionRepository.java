package com.kickoff.be.user.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import com.kickoff.be.user.entity.User;

/**
 * 회원 탈퇴가 지우는 것들 (계약서 §3-4, v1.23.0).
 *
 * <b>삭제 쿼리를 도메인별 리포지토리에 흩지 않고 여기 모은 이유가 있다.</b> 이 기능의
 * 본질은 "무엇을 지우는가"가 아니라 <b>"어떤 순서로 지우는가"</b>다. 외래키가 걸린 순서를
 * 한 번이라도 어기면 탈퇴가 500 으로 막히는데, 열 곳에 흩어져 있으면 그 순서를 통째로
 * 읽을 수 있는 자리가 없어진다. {@code AccountDeletionService} 와 이 파일을 나란히 보면
 * 전체 그림이 한눈에 들어온다.
 *
 * 전부 벌크 JPQL 이다. 엔티티를 하나씩 불러다 지우면 팀 하나에 글 수십·신청 수백이
 * 딸린 계정에서 쿼리가 폭발한다.
 *
 * {@code clearAutomatically} 를 켜 둔 이유: 벌크 삭제는 영속성 컨텍스트를 우회하므로,
 * 안 비우면 <b>이미 지운 행을 캐시가 계속 살아 있다고 말한다.</b>
 */
public interface AccountDeletionRepository extends JpaRepository<User, Long> {

    // ── 차단 조건 (계약서 §3-4)

    /**
     * 경기 예정인 확정 매칭이 있는가. <b>양방향을 다 본다</b> — 내 팀이 글을 쓴 쪽이든
     * 신청한 쪽이든, 사라지면 상대의 확정 매칭이 소리 없이 증발하는 건 똑같다.
     */
    @Query("""
            select count(r) from MatchRequest r
            where r.status = com.kickoff.be.matchrequest.entity.RequestStatus.ACCEPTED
              and r.post.matchAt > :now
              and (r.post.team.id = :teamId or r.applicantTeam.id = :teamId)
            """)
    long countUpcomingAcceptedMatches(@Param("teamId") Long teamId,
                                      @Param("now") OffsetDateTime now);

    /** 내 팀이 당사자인 모든 신청. 아래 삭제들이 이 목록을 기준으로 돈다. */
    @Query("""
            select r.id from MatchRequest r
            where r.post.team.id = :teamId or r.applicantTeam.id = :teamId
            """)
    List<Long> findRequestIdsInvolving(@Param("teamId") Long teamId);

    // ── 팀 딸린 것들 (자식 → 부모 순서)

    /**
     * 상대 팀이 자기 페이지에 가진 수동 전적은 <b>보존하고 연결만 끊는다</b> (계약서 §3-4).
     *
     * 지워 버리면 상대 팀의 전적 목록에서 경기가 통째로 사라진다 — 그 팀은 실제로 그
     * 경기를 했고, 우리가 탈퇴했다고 그 기록까지 없앨 권리는 없다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update TeamRecord tr set tr.request = null where tr.request.id in :requestIds")
    int detachRecordsFromRequests(@Param("requestIds") Collection<Long> requestIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamRecord tr where tr.team.id = :teamId")
    int deleteRecordsOfTeam(@Param("teamId") Long teamId);

    /**
     * 내 팀이 주고받은 리뷰. 지우면 상대 팀 평점이 재계산된다 — 평점은 리뷰에서 매번
     * 집계되므로 따로 손댈 것이 없다 (계약서 §3-4 "상대 팀 평점은 재계산된다").
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Review rv where rv.reviewerTeam.id = :teamId or rv.targetTeam.id = :teamId")
    int deleteReviewsOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatLeave cl where cl.request.id in :requestIds or cl.team.id = :teamId")
    int deleteChatLeaves(@Param("requestIds") Collection<Long> requestIds,
                         @Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ChatMessage cm where cm.request.id in :requestIds")
    int deleteChatMessages(@Param("requestIds") Collection<Long> requestIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from MatchRequest r
            where r.post.team.id = :teamId or r.applicantTeam.id = :teamId
            """)
    int deleteMatchRequestsOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MatchPost p where p.team.id = :teamId")
    int deletePostsOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamMember tm where tm.team.id = :teamId")
    int deleteMembersOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamAdmin ta where ta.team.id = :teamId")
    int deleteAdminsOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamJoinRequest jr where jr.team.id = :teamId")
    int deleteJoinRequestsOfTeam(@Param("teamId") Long teamId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Team t where t.id = :teamId")
    int deleteTeam(@Param("teamId") Long teamId);

    // ── 사용자 딸린 것들 (팀을 안 가진 계정도 여기는 지난다)

    /** 남의 팀 명단에서 빠진다. 명단 항목 자체를 지운다 (계약서 §3-4). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamMember tm where tm.user.id = :userId")
    int deleteMembershipsOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamAdmin ta where ta.user.id = :userId")
    int deleteAdminRolesOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeamJoinRequest jr where jr.user.id = :userId")
    int deleteJoinRequestsOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SupportMessage sm where sm.user.id = :userId")
    int deleteSupportMessagesOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SupportRoom sr where sr.user.id = :userId")
    int deleteSupportRoomOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SocialAccount sa where sa.user.id = :userId")
    int deleteSocialAccountsOf(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from User u where u.id = :userId")
    int deleteUser(@Param("userId") Long userId);
}
