package com.kickoff.be.post;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
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
 * 모집글의 경기장 주소 {@code locationAddress} (계약서 §5, v1.29.0).
 *
 * 장소 검색(§5-1)에서 고르면 FE 가 {@code roadAddress ?? address} 로 채워 보낸다. 서버는
 * <b>길이만 본다</b> — 사용자가 고친 값을 그대로 저장한다.
 *
 * 두 가지를 같은 무게로 고정한다. <b>목록 카드에도 실려야 한다</b>는 것(계약서가 상세와
 * 목록 양쪽에 넣었다 — 카드에 전체 주소를 보여 주는 화면이 있다), 그리고 <b>지울 수
 * 있다</b>는 것이다. 장소 검색으로 고른 글을 직접 입력으로 되돌릴 길이 있어야 한다.
 */
class PostLocationAddressTest extends IntegrationTestSupport {

    private static final String ADDRESS = "서울 강서구 우장산로 72";

    private User author;

    @BeforeEach
    void setUpAuthor() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        createTeam(author, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("주소와 함께 등록하면 상세와 목록 양쪽에 실려 나간다")
    void addressIsStoredAndReturned() throws Exception {
        long postId = idOf(createPost("\"locationAddress\": \"" + ADDRESS + "\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationAddress").value(ADDRESS)));

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationAddress").value(ADDRESS));

        // 목록 카드에도 있어야 한다 — 카드에 전체 주소를 보여 주는 화면이 있다 (v1.29.0)
        mockMvc.perform(get("/api/posts").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].locationAddress").value(ADDRESS));
    }

    /**
     * 주소 없이 등록되는 것이 정상이다 — 장소명을 직접 입력한 글이 그렇다. 키는 남고
     * 값만 null 이다(계약서 §2, {@code default-property-inclusion: always}).
     */
    @Test
    @DisplayName("주소를 안 보내면 null — 키는 남는다")
    void addressIsOptional() throws Exception {
        long postId = idOf(createPost(null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locationAddress").value(nullValue())));

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.locationAddress").value(nullValue()));
        mockMvc.perform(get("/api/posts").param("size", "10"))
                .andExpect(jsonPath("$.content[0].locationAddress").value(nullValue()));
    }

    @Test
    @DisplayName("PATCH 로 주소를 고친다")
    void addressIsPatchable() throws Exception {
        long postId = idOf(createPost(null));

        patchPost(postId, "{\"locationAddress\": \"" + ADDRESS + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationAddress").value(ADDRESS));
    }

    /**
     * <b>명시적 null 이면 지워진다</b> (계약서 §5 의 PATCH 지우기 규칙). 주소는 지울 수
     * 없는 목록(title·content·matchAt·location·region·status)에 없다.
     *
     * 이게 막히면 장소 검색으로 고른 글을 직접 입력으로 되돌릴 수 없다 — 잘못 고른 장소가
     * 글에 영구히 붙는다.
     */
    @Test
    @DisplayName("명시적 null 로 주소를 지운다")
    void addressIsClearable() throws Exception {
        long postId = idOf(createPost("\"locationAddress\": \"" + ADDRESS + "\""));

        patchPost(postId, "{\"locationAddress\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationAddress").value(nullValue()));

        mockMvc.perform(get("/api/posts/{id}", postId))
                .andExpect(jsonPath("$.locationAddress").value(nullValue()));
    }

    /** 필드를 안 보내면 기존 값이 유지된다 — "안 보냄"과 "명시적 null"이 다르다. */
    @Test
    @DisplayName("필드를 빼면 기존 주소가 유지된다")
    void absentFieldKeepsAddress() throws Exception {
        long postId = idOf(createPost("\"locationAddress\": \"" + ADDRESS + "\""));

        patchPost(postId, "{\"title\": \"제목만 고칩니다\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationAddress").value(ADDRESS));
    }

    @Test
    @DisplayName("100자를 넘으면 400")
    void tooLongIsRejected() throws Exception {
        String tooLong = "서".repeat(101);

        createPost("\"locationAddress\": \"" + tooLong + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("locationAddress"));

        long postId = idOf(createPost(null));
        patchPost(postId, "{\"locationAddress\": \"" + tooLong + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("locationAddress"));
    }

    /**
     * 100자는 통과해야 한다. 경계를 함께 고정하지 않으면 "101자를 막는다"만 지켜지고
     * 100자가 조용히 막히는 변경이 들어올 수 있다.
     */
    @Test
    @DisplayName("딱 100자는 통과한다")
    void exactlyHundredIsAccepted() throws Exception {
        createPost("\"locationAddress\": \"" + "서".repeat(100) + "\"")
                .andExpect(status().isCreated());
    }

    private ResultActions createPost(String extraField) throws Exception {
        String matchAt = OffsetDateTime.now().plusDays(7)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String body = """
                {"title": "토요일 아침 11인제 상대 구합니다",
                 "content": "11인제로 2시간 뛸 팀 찾습니다.",
                 "matchAt": "%s",
                 "location": "우장산축구장",
                 "region": "서울 강서구"%s}
                """.formatted(matchAt, extraField == null ? "" : ", " + extraField);
        return mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchPost(long postId, String body) throws Exception {
        return mockMvc.perform(patch("/api/posts/{id}", postId)
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
