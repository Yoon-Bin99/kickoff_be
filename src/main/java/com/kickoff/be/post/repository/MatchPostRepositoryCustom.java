package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.post.service.DateFilter;
import com.kickoff.be.team.entity.SkillLevel;
import java.time.OffsetDateTime;
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

    Page<MatchPost> search(OffsetDateTime now, String region, SkillLevel skillLevel,
                           PostStatus status, String keyword,
                           List<DateFilter.Range> dates, Set<Integer> storedHours,
                           Pageable pageable);
}
