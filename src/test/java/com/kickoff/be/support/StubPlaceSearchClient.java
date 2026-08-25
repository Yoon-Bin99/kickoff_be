package com.kickoff.be.support;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.place.client.PlaceSearchClient;
import com.kickoff.be.place.dto.Place;
import java.util.ArrayList;
import java.util.List;

/**
 * 실제 카카오를 때리지 않고 검색 결과를 대신 돌려준다.
 *
 * 이 스텁은 <b>카카오 응답 → Place 매핑을 검증하지 못한다</b>. 매핑은 실제 클라이언트 안에
 * 있기 때문이다. 그쪽은 KakaoPlaceMappingTest 가 실제 응답 문서로 따로 검증한다.
 */
public class StubPlaceSearchClient implements PlaceSearchClient {

    private final List<Place> nextResult = new ArrayList<>();
    private boolean failNext;
    private String lastQuery;
    private int lastSize;

    @Override
    public List<Place> search(String query, int size) {
        this.lastQuery = query;
        this.lastSize = size;
        if (failNext) {
            throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED);
        }
        return List.copyOf(nextResult);
    }

    public void willReturn(Place... places) {
        nextResult.clear();
        nextResult.addAll(List.of(places));
        failNext = false;
    }

    /** 카카오가 거부하는 상황. */
    public void willFail() {
        this.failNext = true;
    }

    /** 서비스가 카카오에 실제로 넘긴 값 — size 클램프와 공백 정리를 확인한다. */
    public String lastQuery() {
        return lastQuery;
    }

    public int lastSize() {
        return lastSize;
    }

    public void reset() {
        nextResult.clear();
        failNext = false;
        lastQuery = null;
        lastSize = 0;
    }
}
