package com.kickoff.be.place.service;

import com.kickoff.be.place.client.PlaceSearchClient;
import com.kickoff.be.place.dto.PlaceSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 15;

    private final PlaceSearchClient placeSearchClient;

    /**
     * 결과가 없어도 빈 목록이지 에러가 아니다 (계약서 §5-1). FE 는 그때 직접 입력을 안내한다.
     * DB 를 건드리지 않으므로 트랜잭션도 없다.
     */
    public PlaceSearchResponse search(String query, int size) {
        return new PlaceSearchResponse(placeSearchClient.search(query.trim(), clampSize(size)));
    }

    /** 계약서가 최대 15 로 못 박았다. 넘겨도 400 대신 잘라서 보낸다 — 목록 조회와 같은 방식. */
    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
