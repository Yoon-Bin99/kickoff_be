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
                .toList();
    }

    /**
     * 카카오 문서 한 건을 계약서 형태로 옮긴다.
     *
     * <b>x 가 경도, y 가 위도다.</b> 이름만 보면 x=위도로 읽기 쉬운데 반대다. 뒤집혀도 타입과
     * 형식은 멀쩡해서 지도에 찍어보기 전에는 드러나지 않는다 — 그래서 이 메서드를 따로 떼어
     * 실제 카카오 응답으로 단위 테스트를 건다.
     *
     * road_address_name 은 없을 때 null 이 아니라 빈 문자열로 온다. 실제 응답 첫 건부터
     * 그렇게 왔다.
     */
    static Place toPlace(Map<String, Object> document) {
        return new Place(
                (String) document.get("place_name"),
                (String) document.get("address_name"),
                blankToNull((String) document.get("road_address_name")),
                parseCoordinate(document.get("y")),
                parseCoordinate(document.get("x")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 카카오는 좌표를 숫자가 아니라 문자열로 준다. */
    private static double parseCoordinate(Object value) {
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PLACE_SEARCH_FAILED);
        }
    }
}
