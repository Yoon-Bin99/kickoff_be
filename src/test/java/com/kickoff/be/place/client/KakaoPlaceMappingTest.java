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
 * 그래서 실제 카카오 응답 문서로 여기서 못 박는다.
 *
 * <b>v1.37.1 부터 좌표는 읽지 않는다.</b> 전에는 x·y 가 뒤집혀도 타입이 멀쩡해 조용히
 * 틀리는 자리였는데, 이제 그 값을 아예 꺼내지 않으므로 그 사고가 생길 자리가 없다 —
 * 대신 <b>ID 가 없는 줄을 거르는지</b>가 그 자리를 대신한다(아래).
 *
 * 아래 값은 지어낸 게 아니라 실제 API 를 호출해 받은 응답이다.
 */
class KakaoPlaceMappingTest {

    /** 실제 응답 — 도로명 주소가 있는 경우. */
    private static Map<String, Object> withRoadAddress() {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", "26338954");
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
        doc.put("id", "8245930");
        doc.put("place_name", "화명생태공원 중앙광장");
        doc.put("address_name", "부산 북구 화명동 1718-25");
        doc.put("road_address_name", "");
        doc.put("x", "129.00424202369086");
        doc.put("y", "35.22794721853528");
        return doc;
    }

    /**
     * 좌표는 응답에 없다 (계약서 §5-1, v1.37.1).
     *
     * <b>내려 주지 않기로 한 값이라 꺼내지도 않는다.</b> 꺼내 두면 언젠가 어딘가에
     * 저장된다 — 카카오 응답을 저장할 수 없다는 것이 이 절 전체의 전제다.
     */
    @Test
    @DisplayName("Place 에는 좌표 칸이 없다")
    void placeCarriesNoCoordinates() {
        assertThat(Place.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("id", "name", "address", "roadAddress");
    }

    @Test
    @DisplayName("장소 ID 를 옮긴다 — 저장해도 되는 유일한 값이다")
    void idIsCopied() {
        assertThat(KakaoPlaceSearchClient.toPlace(withRoadAddress()).id())
                .isEqualTo("26338954");
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

    /** ID 가 없는 줄을 고르면 저장할 값이 없어 지도를 그릴 수 없다 (계약서 §5-1). */
    @Test
    @DisplayName("ID 가 없거나 비어 있으면 null 로 둔다 — 호출자가 그 줄을 뺀다")
    void missingIdBecomesNull() {
        Map<String, Object> noId = withRoadAddress();
        noId.remove("id");
        assertThat(KakaoPlaceSearchClient.toPlace(noId).id()).isNull();

        Map<String, Object> blankId = withRoadAddress();
        blankId.put("id", "");
        assertThat(KakaoPlaceSearchClient.toPlace(blankId).id()).isNull();
    }
}
