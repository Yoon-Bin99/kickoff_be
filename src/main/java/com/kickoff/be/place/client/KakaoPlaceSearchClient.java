package com.kickoff.be.place.client;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.place.dto.Place;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로컬 키워드 검색 프록시 (계약서 §5-1).
 *
 * BE 가 대행하는 이유는 하나다 — <b>검색 키를 앱 번들에 넣지 않기 위해서</b>다. 클라이언트에
 * 넣으면 디컴파일로 새고, 그 키로 남이 우리 쿼터를 쓴다.
 */
@Slf4j
@Component
public class KakaoPlaceSearchClient implements PlaceSearchClient {

    private static final String SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final PlaceProperties properties;
    private final RestClient restClient;

    public KakaoPlaceSearchClient(PlaceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
        log.info("장소 검색 키 {}", properties.isConfigured() ? "설정됨" : "없음 — 검색이 502 로 나간다");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Place> search(String query, int size) {
        if (!properties.isConfigured()) {
            log.warn("장소 검색 키가 없다 — KAKAO_MAP_REST_KEY 를 확인할 것");
            throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED);
        }
        Map<String, Object> body;
        try {
            // 쿼리를 직접 인코딩해서 붙이면 안 된다. RestClient 는 문자열 URI 를 <b>템플릿</b>으로
            // 보고 한 번 더 인코딩하므로 %EC.. 의 % 가 %25 로 바뀌어 카카오가 엉뚱한 검색어를
            // 받는다. 실제로 그 상태에서 직접 curl 은 2건인데 프록시는 0건이었다.
            // URI 변수로 넘기면 Spring 이 정확히 한 번만 인코딩한다.
            body = restClient.get()
                    .uri(SEARCH_URL + "?size={size}&query={query}", size, query)
                    .header("Authorization", "KakaoAK " + properties.restKey())
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // 카카오가 거부하는 이유는 여러 가지다 (키 무효, 서비스 미활성, 쿼터). 사유를 로그로
            // 남겨야 콘솔 설정 문제인지 코드 문제인지 사후에 구분할 수 있다.
            log.warn("카카오 장소 검색 실패 — {}", e.getMessage());
            throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED);
        }
        if (body == null || !(body.get("documents") instanceof List<?> documents)) {
            log.warn("카카오 장소 검색 응답이 예상과 다르다 — {}", body == null ? "null" : body.keySet());
            throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED);
        }
        return documents.stream()
                .filter(Map.class::isInstance)
                .map(doc -> toPlace((Map<String, Object>) doc))
                // ID 가 없는 줄은 뺀다 (계약서 §5-1, v1.37.0). 고르면 저장할 값이 없어
                // 지도를 그릴 수 없는 줄이다 — 보여 주면 고를 수 있게 되는 것이 문제다.
                .filter(place -> place.id() != null)
                .toList();
    }

    /**
     * 카카오 문서 한 건을 계약서 형태로 옮긴다.
     *
     * <b>좌표(x·y)는 읽지 않는다</b> (계약서 §5-1, v1.37.1). 내려 주지 않기로 한 값이라
     * 꺼낼 이유가 없고, 꺼내 두면 언젠가 어딘가에 저장된다.
     *
     * road_address_name 은 없을 때 null 이 아니라 빈 문자열로 온다. 실제 응답 첫 건부터
     * 그렇게 왔다.
     */
    static Place toPlace(Map<String, Object> document) {
        return new Place(
                blankToNull((String) document.get("id")),
                (String) document.get("place_name"),
                (String) document.get("address_name"),
                blankToNull((String) document.get("road_address_name")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

}
