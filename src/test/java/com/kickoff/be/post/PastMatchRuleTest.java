package com.kickoff.be.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/**
 * 지난 경기는 매칭 대상이 아니다 (계약서 §5).
 * 목록에서는 빠지고, 상세는 열리고, 신청은 막힌다.
 */
class PastMatchRuleTest extends IntegrationTestSupport {

    private User author;
    private User applicant;
    private MatchPost pastPost;
    private MatchPost futurePost;

    @BeforeEach
    void setUpPosts() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicant = createUser("applicant@example.com", "이감독", "010-2222-2222");

        Team authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(applicant, "마포 유나이티드", "서울 마포구");

        // status 는 OPEN 그대로 두고 날짜만 과거로 — 날짜만으로 걸러지는지 보려는 것이다
        pastPost = createPost(authorTeam, "지난 주 경기", OffsetDateTime.now().minusDays(3), true);
        futurePost = createPost(authorTeam, "다음 주 경기", OffsetDateTime.now().plusDays(3), true);
    }

    @Test
    @DisplayName("지난 경기는 status 가 OPEN 이어도 목록에서 빠진다")
    void pastPostIsExcludedFromList() throws Exception {
        mockMvc.perform(get("/api/posts").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(futurePost.getId()));
    }

    @Test
    @DisplayName("status 필터를 무엇으로 주든 지난 경기는 안 나온다")
    void pastPostIsExcludedRegardlessOfStatusFilter() throws Exception {
        for (String status : new String[]{"OPEN", "CLOSED", "MATCHED"}) {
            mockMvc.perform(get("/api/posts").param("status", status).param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[?(@.id == " + pastPost.getId() + ")]").isEmpty());
        }
    }

    @Test
    @DisplayName("다른 필터와 조합해도 마찬가지다")
    void pastPostIsExcludedWithOtherFilters() throws Exception {
        mockMvc.perform(get("/api/posts")
                        .param("region", "서울")
                        .param("fieldType", "FUTSAL")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(futurePost.getId()));
    }

    @Test
    @DisplayName("내 글 목록에는 지난 경기도 남는다 — 내 기록이니까")
    void myPostsKeepPastMatches() throws Exception {
        mockMvc.perform(get("/api/posts/me").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("지난 경기의 상세 조회는 막지 않는다")
    void pastPostDetailIsStillReadable() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", pastPost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pastPost.getId()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("지난 경기에는 status 가 OPEN 이어도 신청할 수 없다 (409 POST_NOT_OPEN)")
    void cannotApplyToPastMatch() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/requests", pastPost.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"지난 경기 신청\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_OPEN"));
    }

    @Test
    @DisplayName("대조군 — 미래 경기에는 정상적으로 신청된다")
    void canApplyToFutureMatch() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/requests", futurePost.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("글 작성 시 과거 시각은 검증에서 걸린다")
    void cannotCreatePostInThePast() throws Exception {
        String body = """
                {"title":"과거 경기","content":"거절되어야 한다",
                 "matchAt":"%s","location":"강서구민운동장","region":"서울 강서구","fieldType":"FUTSAL"}
                """.formatted(OffsetDateTime.now().minusDays(1));

        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("matchAt"));
    }
}
