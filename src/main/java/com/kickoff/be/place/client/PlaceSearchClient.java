package com.kickoff.be.place.client;

import com.kickoff.be.place.dto.Place;
import java.util.List;

/**
 * 장소 검색 경계. 소셜 로그인의 OAuthClient, 푸시의 PushClient 와 같은 이유로 인터페이스를 둔다 —
 * 테스트가 실제 카카오를 때리지 않아야 하고, 제공자를 바꿔도 위 계층이 흔들리지 않아야 한다.
 */
public interface PlaceSearchClient {

    /**
     * 실패하면 BusinessException(PLACE_SEARCH_FAILED) 를 던진다. 결과가 없는 것은 실패가
     * 아니라 빈 목록이다.
     */
    List<Place> search(String query, int size);
}
