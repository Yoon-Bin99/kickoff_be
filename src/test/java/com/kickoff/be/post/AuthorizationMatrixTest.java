package com.kickoff.be.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.Team;
import com.kickoff.be.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 남의 것을 건드리려는 시도는 전부 403 이어야 한다.
 * 한 곳에 모아두면 새 엔드포인트를 추가할 때 빠뜨린 자리가 눈에 띈다.
 */
class AuthorizationMatrixTest extends IntegrationTestSupport {

    private User owner;
    private User outsider;
    private User applicant;
    private Team ownerTeam;
    private MatchPost post;
    private long requestId;

    @BeforeEach
    void setUpActors() throws Exception {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        outsider = createUser("outsider@example.com", "정주장", "010-2222-2222");
        applicant = createUser("applicant@example.com", "이감독", "010-3333-3333");

        ownerTeam = createTeam(owner, "FC 새벽", "서울 강서구");
        createTeam(outsider, "성남 레인저스", "경기 성남시");
        createTeam(applicant, "마포 유나이티드", "서울 마포구");

        post = createPost(ownerTeam, "토요일 아침 풋살 상대 구합니다");
        requestId = idOf(mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("남의 팀은 수정할 수 없다")
    void cannotUpdateOthersTeam() throws Exception {
        expectForbidden(patch("/api/teams/{id}", ownerTeam.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberCount\":99}"));
    }

    @Test
    @DisplayName("남의 글은 수정·삭제할 수 없다")
    void cannotModifyOthersPost() throws Exception {
        expectForbidden(patch("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"제목 바꾸기\"}"));

        expectForbidden(delete("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));

        // 신청한 팀이라고 해서 글을 건드릴 수 있는 것도 아니다
        expectForbidden(delete("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(applicant)));
    }

    @Test
    @DisplayName("글에 온 신청 목록은 작성자만 본다")
    void onlyAuthorReadsIncomingRequests() throws Exception {
        expectForbidden(get("/api/posts/{id}/requests", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));

        // 신청한 당사자도 남의 글의 신청 목록은 못 본다
        expectForbidden(get("/api/posts/{id}/requests", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(applicant)));

        mockMvc.perform(get("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("신청 수락·거절·입금확인은 글 작성자만 한다")
    void onlyAuthorHandlesRequests() throws Exception {
        expectForbidden(post("/api/requests/{id}/accept", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));
        expectForbidden(post("/api/requests/{id}/reject", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));
        expectForbidden(post("/api/requests/{id}/confirm-deposit", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));

        // 신청한 팀이 자기 신청을 스스로 수락할 수는 없다
        expectForbidden(post("/api/requests/{id}/accept", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(applicant)));
    }

    @Test
    @DisplayName("신청 취소는 신청한 팀만 한다 — 글 작성자도 못 한다")
    void onlyApplicantCancels() throws Exception {
        expectForbidden(delete("/api/requests/{id}", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)));
        expectForbidden(delete("/api/requests/{id}", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));

        mockMvc.perform(delete("/api/requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("팀이 없으면 글을 쓸 수 없다 (400 TEAM_REQUIRED)")
    void postingRequiresTeam() throws Exception {
        User teamless = createUser("teamless@example.com", "무소속", "010-9999-9999");
        String body = """
                {"title":"팀 없이 쓰는 글","content":"거절되어야 한다",
                 "matchAt":"%s","location":"어딘가","region":"서울 강남구","fieldType":"FUTSAL"}
                """.formatted(java.time.OffsetDateTime.now().plusDays(3));

        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamless))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_REQUIRED"));

        // 신청도 마찬가지
        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamless))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_REQUIRED"));
    }

    @Test
    @DisplayName("팀이 없는 사용자의 조회성 API 는 에러가 아니라 빈 결과다")
    void readOnlyApisAreEmptyForTeamlessUser() throws Exception {
        User teamless = createUser("teamless@example.com", "무소속", "010-9999-9999");

        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamless)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamless)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/posts/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(teamless)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("한 사용자는 팀을 하나만 갖는다 (409 TEAM_ALREADY_EXISTS)")
    void oneTeamPerUser() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"두 번째 팀","region":"서울 강남구","skillLevel":"AMATEUR",
                                 "ageGroup":"TWENTIES","memberCount":11}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEAM_ALREADY_EXISTS"));
    }

    private void expectForbidden(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
