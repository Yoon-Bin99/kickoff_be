package com.kickoff.be.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.place.dto.Place;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 장소 검색 프록시 (계약서 §5-1). 카카오 호출은 스텁으로 받고, BE 가 책임지는 부분만 본다 —
 * 인증, 파라미터 검증, size 클램프, 실패 변환, 빈 결과 처리.
 *
 * 카카오 응답의 필드 매핑(x/y, 빈 도로명)은 여기서 검증되지 않는다. 그건 KakaoPlaceMappingTest 가 본다.
 */
class PlaceSearchTest extends IntegrationTestSupport {

    private static final Place GANGSEO = new Place("강서구민운동장", "서울 강서구 화곡동 980-16",
            "서울 강서구 남부순환로 172", 37.5586, 126.8351);

    private User user;

    @BeforeEach
    void setUpUser() {
        user = createUser("kim@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("검색하면 계약서 형태로 내려준다")
    void searchReturnsPlaces() throws Exception {
        placeSearchClient.willReturn(GANGSEO);

        search("강서구민운동장", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places", hasSize(1)))
                .andExpect(jsonPath("$.places[0].name").value("강서구민운동장"))
                .andExpect(jsonPath("$.places[0].address").value("서울 강서구 화곡동 980-16"))
                .andExpect(jsonPath("$.places[0].roadAddress").value("서울 강서구 남부순환로 172"))
                .andExpect(jsonPath("$.places[0].latitude").value(37.5586))
                .andExpect(jsonPath("$.places[0].longitude").value(126.8351));
    }

    @Test
    @DisplayName("결과가 없으면 빈 배열이다 — 에러가 아니다")
    void emptyResultIsNotAnError() throws Exception {
        placeSearchClient.willReturn();

        search("있을리없는장소이름", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places", hasSize(0)));
    }

    @Test
    @DisplayName("도로명 주소는 null 일 수 있다")
    void roadAddressIsNullable() throws Exception {
        placeSearchClient.willReturn(new Place("화명생태공원 중앙광장", "부산 북구 화명동 1718-25",
                null, 35.22794721853528, 129.00424202369086));

        search("화명생태공원", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.places[0].roadAddress").isEmpty());
    }

    @Test
    @DisplayName("카카오가 실패하면 502 PLACE_SEARCH_FAILED — FE 는 직접 입력을 연다")
    void kakaoFailureBecomes502() throws Exception {
        placeSearchClient.willFail();

        search("강서구민운동장", null)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_FAILED"));
    }

    @Test
    @DisplayName("size 는 기본 10, 최대 15 로 잘린다")
    void sizeIsClamped() throws Exception {
        placeSearchClient.willReturn();

        search("운동장", null).andExpect(status().isOk());
        assertThat(placeSearchClient.lastSize()).isEqualTo(10);

        search("운동장", 3).andExpect(status().isOk());
        assertThat(placeSearchClient.lastSize()).isEqualTo(3);

        // 계약서 최대치를 넘겨도 400 이 아니라 잘라서 보낸다 (목록 조회와 같은 방식)
        search("운동장", 99).andExpect(status().isOk());
        assertThat(placeSearchClient.lastSize()).isEqualTo(15);

        search("운동장", 0).andExpect(status().isOk());
        assertThat(placeSearchClient.lastSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("검색어 앞뒤 공백은 다듬어 보낸다")
    void queryIsTrimmed() throws Exception {
        placeSearchClient.willReturn();

        search("  강서구민운동장  ", null).andExpect(status().isOk());

        assertThat(placeSearchClient.lastQuery()).isEqualTo("강서구민운동장");
    }

    @Test
    @DisplayName("검색어가 없거나 100자를 넘으면 400")
    void queryIsValidated() throws Exception {
        mockMvc.perform(get("/api/places/search")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        search("가".repeat(101), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 경계값 100자는 통과한다
        placeSearchClient.willReturn();
        search("가".repeat(100), null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인은 검색할 수 없다 — 우리 검색 쿼터를 아무나 쓰면 안 된다")
    void anonymousCannotSearch() throws Exception {
        mockMvc.perform(get("/api/places/search").param("query", "강서구민운동장"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions search(String query, Integer size) throws Exception {
        var request = get("/api/places/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .param("query", query);
        if (size != null) {
            request = request.param("size", String.valueOf(size));
        }
        return mockMvc.perform(request);
    }
}
