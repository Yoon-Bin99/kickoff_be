package com.kickoff.be.stadium.repository;

import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.entity.StadiumSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StadiumRepository extends JpaRepository<Stadium, Long> {

    /**
     * 구장 목록 (계약서 §8-1, v1.27.0). 인증 불필요.
     *
     * <b>지역은 프리픽스, 이름은 부분 일치다.</b> 계약서가 그렇게 정했다 — "서울"이면 서울
     * 전체가 나와야 하므로 지역은 앞에서부터 맞춰 보고("서울" → "서울 강서구" 매칭),
     * 이름은 §5 의 검색과 같이 가운데가 걸려도 찾힌다. 지역을 부분 일치로 두면 "중구"가
     * "서울 중구"와 "인천 중구"를 한꺼번에 끌어오는데, 그건 시 단위로 좁히려는 사용자에게
     * 오히려 방해가 된다.
     *
     * escape '!' 는 검색어의 {@code %}·{@code _} 를 리터럴로 다루기 위한 것이다
     * ({@link com.kickoff.be.common.LikeEscape}). 이스케이프는 서비스가 걸어서 넘기고
     * 여기서는 그 문자가 무엇인지만 알려 준다 — <b>양쪽이 다 있어야 한다.</b> 지역 쪽에도
     * 빠뜨리면 안 된다. 프리픽스라 티가 덜 날 뿐 {@code %} 를 친 사람에게 전체 목록이 나간다.
     *
     * cast(:x as string) 는 없어도 될 것 같지만 필요하다. 파라미터가 null 일 때 H2 는
     * 넘어가지만 PostgreSQL 은 타입을 못 정하고 터진다 (팀 검색에서 같은 함정을 겪었다).
     *
     * 정렬은 region → name 이다(계약서 §8-1). 동명이 있을 수 있어 id 를 마지막 키로 넣는다 —
     * 없으면 같은 이름 둘의 상대 순서가 조회마다 달라져 페이징에서 하나가 두 번 나오거나 빠진다.
     */
    @Query("""
            select s from Stadium s
            where (:region is null
                   or s.region like concat(cast(:region as string), '%') escape '!')
              and (:keyword is null
                   or lower(s.name) like lower(concat('%', cast(:keyword as string), '%')) escape '!')
            order by s.region asc, s.name asc, s.id asc
            """)
    Page<Stadium> search(@Param("region") String region, @Param("keyword") String keyword,
                         Pageable pageable);

    Optional<Stadium> findByExternalId(String externalId);

    /** 동기화가 한 번에 다 읽어 메모리에서 짝을 맞춘다 — 건마다 조회하면 수백 번이 된다. */
    List<Stadium> findAllBySource(StadiumSource source);

    long countBySource(StadiumSource source);
}
