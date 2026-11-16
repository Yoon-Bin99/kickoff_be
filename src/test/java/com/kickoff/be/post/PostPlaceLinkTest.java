package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 모집글의 장소 연결 (계약서 §5-1, v1.37.0).
 *
 * <b>카카오 로컬 검색의 응답은 저장할 수 없다</b>(카카오 운영자 답변). 저장해도 되는 것은
 * 장소 ID 와 사용자가 직접 적은 글자뿐이라, 좌표 두 칸이 사라지고 {@code kakaoPlaceId}·
 * {@code placeQuery} 한 쌍이 그 자리에 들어왔다. 지도는 보는 사람의 기기가 그 둘로 다시
 * 조회해 그린다.
 *
 * 쌍으로만 다루는 것이 이 묶음의 전부다 — 한쪽만 남으면 기기가 그 장소를 찾을 수 없다.
 */
class PostPlaceLinkTest extends IntegrationTestSupport {

    private User author;
    private Team team;

    @BeforeEach
    void setUpAuthor() {
        author = createUser("author@example.com", "김작성", "010-1111-1111");
        team = createTeam(author, "FC 새벽", "서울 강서구");
    }

    // ── 작성

    @Test
    @DisplayName("장소 연결과 함께 글을 쓸 수 있고 상세에 실린다")
    void creatingWithPlaceLink() throws Exception {
        create("\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kakaoPlaceId").value("8245930"))
                .andExpect(jsonPath("$.placeQuery").value("우장산축구장"));
    }

    @Test
    @DisplayName("장소 연결 없이도 글은 등록된다 — 직접 입력한 경우다")
    void creatingWithoutPlaceLink() throws Exception {
        create(null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kakaoPlaceId").doesNotExist())
                .andExpect(jsonPath("$.placeQuery").doesNotExist());
    }

    /** ID 만 있으면 기기가 그 장소를 찾을 검색어가 없다 — 지도를 그릴 수 없는 반쪽이다. */
    @Test
    @DisplayName("한쪽만 보내면 400 이고 빠진 칸을 가리킨다")
    void halfOfThePairIsRejected() throws Exception {
        create("\"kakaoPlaceId\":\"8245930\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'placeQuery')]").exists());

        create("\"placeQuery\":\"우장산축구장\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kakaoPlaceId')]").exists());
    }

    @Test
    @DisplayName("숫자가 아닌 장소 ID 는 400 이다")
    void nonNumericIdIsRejected() throws Exception {
        create("\"kakaoPlaceId\":\"abc123\",\"placeQuery\":\"우장산축구장\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kakaoPlaceId')]").exists());
    }

    @Test
    @DisplayName("검색어는 앞뒤 공백을 떼고 저장한다")
    void queryIsTrimmed() throws Exception {
        create("\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"  우장산축구장  \"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.placeQuery").value("우장산축구장"));
    }

    /** 공백만 보낸 검색어는 없는 것과 같다 — 그러면 ID 만 남아 짝이 깨진다. */
    @Test
    @DisplayName("공백뿐인 검색어는 400 이다")
    void blankQueryIsRejected() throws Exception {
        create("\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"   \"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'placeQuery')]").exists());
    }

    // ── 좌표 폐지 (계약서 §5-1, v1.37.0)

    /**
     * 요청에 와도 무시한다. 400 으로 막지 않는 것은 <b>구버전 앱</b> 때문이다 — 좌표를
     * 실어 보내던 앱이 그 때문에 글을 못 쓰게 되면, 쓸 수 없는 이유가 화면에 드러나지도
     * 않는다. 저장되지 않는 것으로 충분하다.
     */
    @Test
    @DisplayName("좌표를 보내도 무시되고 응답에 없다")
    void coordinatesAreIgnored() throws Exception {
        create("\"latitude\":37.5504,\"longitude\":126.8451")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist());
    }

    @Test
    @DisplayName("목록에도 좌표가 없고 장소 연결도 싣지 않는다")
    void listCarriesNeither() throws Exception {
        createPost(team, "토요일 아침 상대 구합니다");

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].latitude").doesNotExist())
                .andExpect(jsonPath("$.content[0].longitude").doesNotExist())
                // 목록은 지도를 그리지 않는다 (계약서 §5-1)
                .andExpect(jsonPath("$.content[0].kakaoPlaceId").doesNotExist());
    }

    // ── 수정

    @Test
    @DisplayName("PATCH 로 장소를 연결하고 바꿀 수 있다")
    void patchingSetsAndChangesTheLink() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoPlaceId").value("8245930"));

        patchPost(post, "{\"kakaoPlaceId\":\"111\",\"placeQuery\":\"강서구민운동장\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoPlaceId").value("111"))
                .andExpect(jsonPath("$.placeQuery").value("강서구민운동장"));
    }

    @Test
    @DisplayName("둘 다 null 을 보내면 연결이 끊긴다")
    void bothNullClearsTheLink() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");
        patchPost(post, "{\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"}")
                .andExpect(status().isOk());

        patchPost(post, "{\"kakaoPlaceId\":null,\"placeQuery\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoPlaceId").doesNotExist())
                .andExpect(jsonPath("$.placeQuery").doesNotExist());
    }

    @Test
    @DisplayName("둘 다 안 보내면 기존 연결이 그대로 남는다")
    void omittingBothKeepsTheLink() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");
        patchPost(post, "{\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"}")
                .andExpect(status().isOk());

        patchPost(post, "{\"title\":\"제목만 고칩니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakaoPlaceId").value("8245930"));
    }

    /**
     * <b>판정은 요청 본문 기준이다</b> (계약서 §5-1). 병합 결과로 보면 "ID 만 지우는"
     * 요청이 기존 검색어와 짝이 맞는 것처럼 보여 통과하고, 기기가 찾을 수 없는 반쪽
     * 연결이 남는다.
     */
    @Test
    @DisplayName("PATCH 에서 한쪽만 보내면 400 이다 — 기존 값과 짝을 맞춰 주지 않는다")
    void patchingHalfIsRejected() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");
        patchPost(post, "{\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"}")
                .andExpect(status().isOk());

        patchPost(post, "{\"kakaoPlaceId\":null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'placeQuery')]").exists());
        patchPost(post, "{\"placeQuery\":\"다른 검색어\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kakaoPlaceId')]").exists());

        // 아무것도 바뀌지 않았다
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.kakaoPlaceId").value("8245930"))
                .andExpect(jsonPath("$.placeQuery").value("우장산축구장"));
    }

    /** PATCH 는 어노테이션 검증을 못 탄다 (Patchable 안의 값이라서) — 서비스가 본다. */
    @Test
    @DisplayName("PATCH 의 형식 검증도 동작한다")
    void patchingValidatesFormat() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"kakaoPlaceId\":\"abc\",\"placeQuery\":\"우장산축구장\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'kakaoPlaceId')]").exists());
    }

    @Test
    @DisplayName("PATCH 로 좌표를 보내도 무시된다")
    void patchingCoordinatesIsIgnored() throws Exception {
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"latitude\":37.5,\"longitude\":126.8}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").doesNotExist());
    }

    @Test
    @DisplayName("저장된 값은 엔티티에도 쌍으로 남는다")
    void storedAsAPair() throws Exception {
        create("\"kakaoPlaceId\":\"8245930\",\"placeQuery\":\"우장산축구장\"")
                .andExpect(status().isCreated());

        assertThat(postRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getKakaoPlaceId()).isEqualTo("8245930");
            assertThat(saved.getPlaceQuery()).isEqualTo("우장산축구장");
            assertThat(saved.hasPlaceLink()).isTrue();
        });
    }

    private ResultActions create(String extraFields) throws Exception {
        String body = """
                {"title":"토요일 아침 상대 구합니다","content":"내용입니다.",
                 "matchAt":"%s","location":"우장산축구장","region":"서울 강서구"%s}
                """.formatted(OffsetDateTime.now().plusDays(7),
                extraFields == null ? "" : "," + extraFields);
        return mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchPost(MatchPost post, String body) throws Exception {
        return mockMvc.perform(patch("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
