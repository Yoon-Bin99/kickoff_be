package com.kickoff.be.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kickoff.be.place.dto.Place;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 카카오로 <b>나가는 요청</b>을 고정한다.
 *
 * 이 테스트가 있는 이유는 실제로 당한 버그 때문이다. 검색어를 직접 URLEncoder 로 인코딩해서
 * 문자열 URI 에 붙였더니, RestClient 가 그 문자열을 템플릿으로 보고 한 번 더 인코딩해
 * `%EC..` 의 `%` 가 `%25` 가 됐다. 카카오는 200 에 빈 결과를 주고, 우리는 200 에 빈 배열을
 * 내려줬다 — 어디에도 에러가 없어서 스텁 기반 통합 테스트 8개가 전부 초록불이었고,
 * 실제 키로 호출해 보고서야 드러났다.
 *
 * 그래서 여기서는 응답이 아니라 <b>요청 URI 자체</b>를 본다.
 */
class KakaoPlaceSearchClientTest {

    private static final String SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final String BODY = """
            {"documents":[{"place_name":"강서구민운동장","address_name":"서울 강서구 화곡동 980-16",
            "road_address_name":"서울 강서구 남부순환로 172","x":"126.8351","y":"37.5586"}]}""";

    @Test
    @DisplayName("한글 검색어는 정확히 한 번만 인코딩돼 나간다")
    void queryIsEncodedExactlyOnce() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // 강서구민운동장 을 UTF-8 로 한 번 인코딩한 결과. 두 번 인코딩되면 %25EA.. 가 돼 어긋난다.
        server.expect(requestTo(SEARCH_URL + "?size=5"
                        + "&query=%EA%B0%95%EC%84%9C%EA%B5%AC%EB%AF%BC%EC%9A%B4%EB%8F%99%EC%9E%A5"))
                .andExpect(header("Authorization", "KakaoAK test-key"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        List<Place> places = client(builder).search("강서구민운동장", 5);

        assertThat(places).singleElement()
                .extracting(Place::name, Place::latitude, Place::longitude)
                .containsExactly("강서구민운동장", 37.5586, 126.8351);
        server.verify();
    }

    @Test
    @DisplayName("공백·+ 가 섞인 검색어도 그대로 전달된다")
    void spacesSurviveEncoding() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(SEARCH_URL + "?size=10&query=Olympic%20Park%20FC"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        client(builder).search("Olympic Park FC", 10);

        server.verify();
    }

    private static KakaoPlaceSearchClient client(RestClient.Builder builder) {
        return new KakaoPlaceSearchClient(new PlaceProperties("test-key"), builder);
    }
}
