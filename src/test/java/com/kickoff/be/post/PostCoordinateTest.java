package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 모집글 좌표 (계약서 §5-1).
 *
 * 좌표는 <b>쌍으로만</b> 다뤄야 한다. 한쪽만 들어오면 이전 값과 짝지어져 엉뚱한 지점을
 * 가리키는데, 형식상으로는 멀쩡해 보여서 지도에 찍어보기 전엔 아무도 모른다. 그래서
 * 요청 단계에서 막고, 그 규칙을 여기서 고정한다.
 */
class PostCoordinateTest extends IntegrationTestSupport {

    /** 계약서 §5-1 예시의 강서구민운동장 좌표. */
    private static final double LAT = 37.5586;
    private static final double LNG = 126.8351;

    private User author;

    @BeforeEach
    void setUpAuthor() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        createTeam(author, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("좌표와 함께 등록하면 상세와 목록 양쪽에 실려 나간다")
    void coordinatesAreStoredAndReturned() throws Exception {
        long postId = idOf(createPost(coordinateJson(LAT, LNG))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").value(LAT))
                .andExpect(jsonPath("$.longitude").value(LNG)));

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(LAT))
                .andExpect(jsonPath("$.longitude").value(LNG));

        // 목록 카드에도 있어야 FE 가 지도 화면에 여러 글을 한 번에 찍을 수 있다
        mockMvc.perform(get("/api/posts").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].latitude").value(LAT))
                .andExpect(jsonPath("$.content[0].longitude").value(LNG));
    }

    @Test
    @DisplayName("좌표 없이도 등록된다 — 장소를 직접 입력한 글이다")
    void coordinatesAreOptional() throws Exception {
        createPost(null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.latitude").isEmpty())
                .andExpect(jsonPath("$.longitude").isEmpty());
    }

    @Test
    @DisplayName("위도만 보내면 400 — 경도가 빠졌다고 알려준다")
    void latitudeAloneIsRejected() throws Exception {
        createPost("\"latitude\": " + LAT)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("longitude"));
    }

    @Test
    @DisplayName("경도만 보내면 400 — 위도가 빠졌다고 알려준다")
    void longitudeAloneIsRejected() throws Exception {
        createPost("\"longitude\": " + LNG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("latitude"));
    }

    @Test
    @DisplayName("위도는 -90~90, 경도는 -180~180 을 벗어나면 400")
    void coordinatesOutOfRangeAreRejected() throws Exception {
        createPost(coordinateJson(90.1, LNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("latitude"));

        createPost(coordinateJson(-90.1, LNG))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("latitude"));

        createPost(coordinateJson(LAT, 180.1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("longitude"));

        createPost(coordinateJson(LAT, -180.1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("longitude"));

        assertThat(postRepository.count()).isZero();
    }

    @Test
    @DisplayName("경계값은 통과한다 — 90, 180 은 유효한 좌표다")
    void boundaryCoordinatesAreAccepted() throws Exception {
        createPost(coordinateJson(90.0, 180.0)).andExpect(status().isCreated());
        createPost(coordinateJson(-90.0, -180.0)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PATCH 로 좌표를 쌍으로 고칠 수 있다")
    void coordinatesCanBeUpdatedAsPair() throws Exception {
        long postId = idOf(createPost(coordinateJson(LAT, LNG)).andExpect(status().isCreated()));

        mockMvc.perform(patch("/api/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 37.5169, \"longitude\": 127.1233}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.5169))
                .andExpect(jsonPath("$.longitude").value(127.1233));
    }

    @Test
    @DisplayName("PATCH 에 한쪽만 보내면 400 — 옛 값과 짝지어져 엉뚱한 지점이 되는 걸 막는다")
    void patchWithSingleCoordinateIsRejected() throws Exception {
        long postId = idOf(createPost(coordinateJson(LAT, LNG)).andExpect(status().isCreated()));

        mockMvc.perform(patch("/api/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\": 35.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("longitude"));

        // 원래 좌표가 그대로여야 한다
        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.latitude").value(LAT))
                .andExpect(jsonPath("$.longitude").value(LNG));
    }

    @Test
    @DisplayName("좌표를 건드리지 않는 PATCH 는 기존 좌표를 지우지 않는다")
    void patchWithoutCoordinatesKeepsThem() throws Exception {
        long postId = idOf(createPost(coordinateJson(LAT, LNG)).andExpect(status().isCreated()));

        mockMvc.perform(patch("/api/posts/{id}", postId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"제목만 고칩니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("제목만 고칩니다"))
                .andExpect(jsonPath("$.latitude").value(LAT))
                .andExpect(jsonPath("$.longitude").value(LNG));
    }

    private static String coordinateJson(double latitude, double longitude) {
        return "\"latitude\": " + latitude + ", \"longitude\": " + longitude;
    }

    /** coordinates 는 JSON 조각이다. null 이면 좌표 없는 요청이 된다. */
    private ResultActions createPost(String coordinates) throws Exception {
        String matchAt = OffsetDateTime.now().plusDays(7)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String body = """
                {"title": "토요일 아침 풋살 상대 구합니다",
                 "content": "6인제로 2시간 뛸 팀 찾습니다.",
                 "matchAt": "%s",
                 "location": "강서구민운동장 A구장",
                 "region": "서울 강서구",
                 "fieldType": "FUTSAL"%s}
                """.formatted(matchAt, coordinates == null ? "" : ", " + coordinates);
        return mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
