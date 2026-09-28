package com.kickoff.be.stadium.service;

import com.kickoff.be.common.LikeEscape;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.stadium.dto.StadiumSummary;
import com.kickoff.be.stadium.entity.Stadium;
import com.kickoff.be.stadium.repository.StadiumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구장 디렉터리 조회 (계약서 §8-1, v1.27.0).
 *
 * 조회만 한다. 채워 넣는 쪽은 {@link SeoulStadiumSyncService}(공공 API)와 시드(수동)다 —
 * <b>여기서 외부를 부르지 않는다.</b> 요청 경로에 외부 호출이 끼면 공공 API 가 느린 날
 * 목록 화면이 통째로 멈춘다.
 */
@Service
@RequiredArgsConstructor
public class StadiumService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final StadiumRepository stadiumRepository;

    @Transactional(readOnly = true)
    public PageResponse<StadiumSummary> search(String region, String keyword, int page, int size) {
        Page<Stadium> stadiums = stadiumRepository.search(
                escape(region), escape(keyword),
                PageRequest.of(Math.max(page, 0), clampSize(size)));
        return PageResponse.of(stadiums, StadiumSummary::of);
    }

    /**
     * 검색어의 LIKE 와일드카드를 리터럴로 만든다. 쿼리의 {@code escape '!'} 절과 짝이라
     * 한쪽만 있으면 오히려 나빠진다 ({@link LikeEscape} 주석 참고).
     *
     * 빈 문자열은 필터를 안 건 것과 같이 본다 — FE 가 검색창을 비우면 빈 값이 오는데,
     * 그걸 필터로 받으면 지역 프리픽스 {@code ''} 가 모든 행에 걸려 결과는 같고 인덱스만
     * 못 타게 된다.
     */
    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LikeEscape.escape(value.strip());
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
