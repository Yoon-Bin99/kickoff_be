package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.post.service.DateFilter;
import com.kickoff.be.team.entity.SkillLevel;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 목록 필터 (계약서 §5). 날짜 조건이 <b>개수가 정해지지 않은 OR</b> 이라 정적 @Query 로는
 * 담을 수 없어 직접 만든다.
 *
 * 날짜를 KST 로 바꿔 판정하는 방법은 두 가지였다 — SQL 에서 타임존을 변환하거나, 자바에서
 * 시각 범위로 바꿔 부등호만 넘기거나. 후자를 택했다. 타임존 변환 함수는 H2 와 PostgreSQL
 * 에서 갈리는 대표적인 자리이고, 범위 비교에는 두 DB 가 다르게 동작할 여지가 없다.
 */
public interface MatchPostRepositoryCustom {

    /**
     * @param excludedPostIds 이 조회에서 빼야 할 글. 보는 사람이 신고한 글이 여기 온다
     *                        (계약서 §7-2 — 신고한 사람 화면에서는 바로 빠진다).
     *                        <b>조건을 서비스가 만들어 넘긴다</b>: 신고 쪽 표를 글 조회가
     *                        직접 조인하면 두 도메인이 쿼리 하나에 엉킨다.
     */
    Page<MatchPost> search(OffsetDateTime now, String region, SkillLevel skillLevel,
                           PostStatus status, String keyword,
                           List<DateFilter.Range> dates, Set<Integer> storedHours,
                           Collection<Long> excludedPostIds, Collection<Long> excludedTeamIds,
                           Pageable pageable);
}
