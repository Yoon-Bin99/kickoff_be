package com.kickoff.be.place.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.place.dto.Place;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카카오 응답 → Place 매핑.
 *
 * 통합 테스트는 클라이언트를 스텁으로 갈아끼우기 때문에 <b>이 매핑을 검증하지 못한다</b>.
 * 그런데 여기가 정확히 조용히 틀리는 자리다 — x 와 y 를 뒤집어도 타입도 형식도 멀쩡하고,
 * 지도에 찍어보기 전에는 아무도 모른다. 그래서 실제 카카오 응답 문서로 여기서 못 박는다.
 *
 * 아래 값은 지어낸 게 아니라 실제 API 를 호출해 받은 응답이다.
 */
class KakaoPlaceMappingTest {

    /** 실제 응답 — 도로명 주소가 있는 경우. */
    private static Map<String, Object> withRoadAddress() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("place_name", "화명운동장 1주차장");
        doc.put("address_name", "부산 북구 화명동 1718-7");
        doc.put("road_address_name", "부산 북구 생태공원길 270");
        doc.put("x", "129.00795381263296");
        doc.put("y", "35.2397404679244");
        return doc;
    }

    /** 실제 응답 — 도로명 주소가 <b>빈 문자열</b>로 오는 경우. 첫 검색부터 이렇게 왔다. */
    private static Map<String, Object> withoutRoadAddress() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("place_name", "화명생태공원 중앙광장");
        doc.put("address_name", "부산 북구 화명동 1718-25");
        doc.put("road_address_name", "");
        doc.put("x", "129.00424202369086");
        doc.put("y", "35.22794721853528");
        return doc;
    }

    @Test
    @DisplayName("x 는 경도, y 는 위도다 — 이름이 아니라 값의 범위로 확인한다")
    void xIsLongitudeAndYIsLatitude() {
        Place place = KakaoPlaceSearchClient.toPlace(withRoadAddress());

        assertThat(place.latitude()).isEqualTo(35.2397404679244);
        assertThat(place.longitude()).isEqualTo(129.00795381263296);

        // 필드명만 맞춰두면 뒤집혀도 통과한다. 한국 좌표 범위로 의미까지 고정한다 —
        // 위도(33~39)와 경도(124~132)는 겹치지 않아서 바뀌면 반드시 걸린다.
        assertThat(place.latitude()).isBetween(33.0, 39.0);
        assertThat(place.longitude()).isBetween(124.0, 132.0);
    }

    @Test
    @DisplayName("도로명 주소가 빈 문자열이면 null 로 바꾼다 — 계약이 nullable 로 규정한다")
    void blankRoadAddressBecomesNull() {
        assertThat(KakaoPlaceSearchClient.toPlace(withoutRoadAddress()).roadAddress()).isNull();
        assertThat(KakaoPlaceSearchClient.toPlace(withRoadAddress()).roadAddress())
                .isEqualTo("부산 북구 생태공원길 270");
    }

    @Test
    @DisplayName("이름과 지번 주소는 그대로 옮긴다")
    void nameAndAddressAreCopied() {
        Place place = KakaoPlaceSearchClient.toPlace(withoutRoadAddress());

        assertThat(place.name()).isEqualTo("화명생태공원 중앙광장");
        assertThat(place.address()).isEqualTo("부산 북구 화명동 1718-25");
    }

    @Test
    @DisplayName("좌표는 문자열로 온다 — 숫자로 바꿔서 내보낸다")
    void coordinatesArriveAsStrings() {
        // 카카오는 x/y 를 JSON 문자열로 준다. 그대로 흘리면 FE 가 숫자로 못 쓴다.
        assertThat(withRoadAddress().get("x")).isInstanceOf(String.class);
        assertThat(KakaoPlaceSearchClient.toPlace(withRoadAddress()).longitude())
                .isInstanceOf(Double.class);
    }
}
