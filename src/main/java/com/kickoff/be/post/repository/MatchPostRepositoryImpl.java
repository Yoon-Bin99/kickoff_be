package com.kickoff.be.post.repository;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.post.service.DateFilter;
import com.kickoff.be.team.entity.SkillLevel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class MatchPostRepositoryImpl implements MatchPostRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<MatchPost> search(OffsetDateTime now, String region, SkillLevel skillLevel,
                                  PostStatus status, String keyword,
                                  List<DateFilter.Range> dates, Set<Integer> storedHours,
                                  Pageable pageable) {
        List<String> where = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        // 지난 경기는 status 와 무관하게 빠진다 — 매칭 대상이 아니라서다 (계약서 §5).
        where.add("p.matchAt >= :now");
        params.put("now", now);

        // 파라미터를 <b>있을 때만</b> 바인딩한다. 예전 정적 쿼리는 null 을 넘기고
        // ":x is null or ..." 로 걸렀는데, 그때 PostgreSQL 이 타입 없는 null 을 bytea 로
        // 추론해 lower(bytea) 를 찾다 실패하는 함정이 있어 cast 로 막아야 했다.
        // 조건 자체를 안 붙이면 그 함정이 생길 자리가 없다.
        if (region != null) {
            where.add("lower(p.region) like lower(concat('%', :region, '%'))");
            params.put("region", region);
        }
        if (skillLevel != null) {
            where.add("p.preferredSkillLevel = :skillLevel");
            params.put("skillLevel", skillLevel);
        }
        if (status != null) {
            where.add("p.status = :status");
            params.put("status", status);
        }
        if (keyword != null) {
            where.add("(lower(p.title) like lower(concat('%', :keyword, '%'))"
                    + " or lower(p.content) like lower(concat('%', :keyword, '%')))");
            params.put("keyword", keyword);
        }

        // 날짜: 고른 날들 중 <b>어느 하나</b>에 속하면 된다 (OR). 각 날은 KST 하루의
        // 시각 범위로 바뀌어 들어오므로 여기서는 부등호만 쓴다.
        if (!dates.isEmpty()) {
            List<String> clauses = new ArrayList<>();
            for (int i = 0; i < dates.size(); i++) {
                clauses.add("(p.matchAt >= :dateStart%d and p.matchAt < :dateEnd%d)".formatted(i, i));
                params.put("dateStart" + i, dates.get(i).start());
                params.put("dateEnd" + i, dates.get(i).end());
            }
            where.add("(" + String.join(" or ", clauses) + ")");
        }

        // 시간대: KST 시각이 <b>저장된 시</b> 집합으로 바뀌어 들어온다. 저장 시각이 KST 와
        // 같으리라 가정하면 배포 환경에서 아홉 시간 어긋난다 (MatchAtStorage 참고).
        if (!storedHours.isEmpty()) {
            where.add("extract(hour from p.matchAt) in :storedHours");
            params.put("storedHours", storedHours);
        }

        String predicate = String.join("\n  and ", where);
        TypedQuery<MatchPost> query = entityManager.createQuery(
                "select p from MatchPost p join fetch p.team\nwhere " + predicate
                        + orderBy(pageable.getSort()), MatchPost.class);
        TypedQuery<Long> countQuery = entityManager.createQuery(
                "select count(p) from MatchPost p\nwhere " + predicate, Long.class);
        params.forEach((name, value) -> {
            query.setParameter(name, value);
            countQuery.setParameter(name, value);
        });

        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, countQuery.getSingleResult());
    }

    /**
     * 정렬은 호출자가 준 Sort 를 그대로 따른다. 비어 있으면 붙이지 않는다 — 그때는
     * 순서가 DB 마음이라 페이징이 흔들리므로, 호출자가 늘 지정해야 한다 (§5 는 matchAt
     * 오름차순 고정이다).
     */
    private String orderBy(Sort sort) {
        if (sort.isEmpty()) {
            return "";
        }
        return "\norder by " + sort.stream()
                .map(order -> "p." + order.getProperty() + " "
                        + (order.isAscending() ? "asc" : "desc"))
                .collect(Collectors.joining(", "));
    }
}
