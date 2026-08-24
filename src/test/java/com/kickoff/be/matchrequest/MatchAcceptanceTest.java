package com.kickoff.be.matchrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 수락은 신청 하나가 아니라 글과 나머지 신청까지 함께 바뀌는, 이 도메인에서 가장 복잡한
 * 상태 전이다. 계약서 §6.
 */
class MatchAcceptanceTest extends IntegrationTestSupport {

    private User author;
    private User applicantB;
    private User applicantC;
    private MatchPost post;
    private long requestB;
    private long requestC;

    @BeforeEach
    void setUpMatch() throws Exception {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicantB = createUser("b@example.com", "이감독", "010-2222-2222");
        applicantC = createUser("c@example.com", "박캡틴", "010-3333-3333");

        Team authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(applicantB, "마포 유나이티드", "서울 마포구");
        createTeam(applicantC, "송파 FC", "서울 송파구");

        post = createPost(authorTeam, "토요일 아침 풋살 상대 구합니다");
        requestB = apply(applicantB, "마포에서 갑니다");
        requestC = apply(applicantC, "송파에서 갑니다");
    }

    @Test
    @DisplayName("수락하면 글이 MATCHED 가 되고 같은 글의 나머지 PENDING 은 전부 자동 거절된다")
    void acceptMatchesPostAndRejectsCompetingRequests() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/accept", requestB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.postStatus").value("MATCHED"));

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.MATCHED);
        assertThat(requestRepository.findById(requestB).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.ACCEPTED);
        assertThat(requestRepository.findById(requestC).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    @DisplayName("수락된 두 팀만 서로의 연락처를 본다 — 거절된 팀과 비로그인은 못 본다")
    void contactIsVisibleOnlyToTheMatchedPair() throws Exception {
        accept(requestB);

        // 작성자는 수락한 상대 팀의 연락처를 본다
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.nickname").value("이감독"))
                .andExpect(jsonPath("$.contact.phone").value("010-2222-2222"));

        // 수락된 신청 팀은 글 작성 팀의 연락처를 본다
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact.nickname").value("김주장"))
                .andExpect(jsonPath("$.contact.phone").value("010-1111-1111"));

        // 자동 거절된 팀은 못 본다
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantC)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").isEmpty())
                .andExpect(jsonPath("$.myRequestStatus").value("REJECTED"));

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").isEmpty());
    }

    @Test
    @DisplayName("이미 처리된 신청을 다시 수락하면 409 REQUEST_NOT_PENDING")
    void acceptingTwiceFails() throws Exception {
        accept(requestB);

        mockMvc.perform(post("/api/requests/{id}/accept", requestB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_PENDING"));

        // 자동 거절된 신청도 마찬가지
        mockMvc.perform(post("/api/requests/{id}/accept", requestC)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_PENDING"));
    }

    @Test
    @DisplayName("MATCHED 된 글에는 새 신청을 받지 않는다 (409 POST_NOT_OPEN)")
    void matchedPostRejectsNewRequests() throws Exception {
        accept(requestB);

        User late = createUser("late@example.com", "최총무", "010-4444-4444");
        createTeam(late, "고양 킥커스", "경기 고양시");

        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(late))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"뒤늦게 신청합니다\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_OPEN"));
    }

    @Test
    @DisplayName("거절은 글 상태를 건드리지 않는다 — 다른 팀이 계속 신청할 수 있다")
    void rejectKeepsPostOpen() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/reject", requestC)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.postStatus").value("OPEN"));

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.OPEN);
        assertThat(requestRepository.findById(requestB).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @DisplayName("requestCount 는 살아 있는 신청만 센다 — 자동 거절되면 줄어든다")
    void requestCountCountsOnlyLiveRequests() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.requestCount").value(2));

        accept(requestB);

        // B 는 ACCEPTED 로 남고 C 는 REJECTED 로 빠진다
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.requestCount").value(1));
    }

    @Test
    @DisplayName("취소·거절 이력이 있으면 재신청할 수 있다")
    void reapplyAfterCancelIsAllowed() throws Exception {
        mockMvc.perform(delete("/api/requests/{id}", requestC)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantC)))
                .andExpect(status().isNoContent());

        assertThat(requestRepository.findById(requestC).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CANCELED);

        long reapplied = apply(applicantC, "다시 신청합니다");
        assertThat(requestRepository.findById(reapplied).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @DisplayName("같은 팀이 살아 있는 신청을 두 번 걸 수는 없다 (409 DUPLICATE_REQUEST)")
    void duplicateRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"또 신청\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("자기 팀 글에는 신청할 수 없다 (400 SELF_REQUEST_NOT_ALLOWED)")
    void cannotApplyToOwnPost() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"내 글에 신청\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_REQUEST_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("받은·보낸·글별 어느 목록에서든 매칭의 양 팀이 다 담긴다 (계약서 v1.2.1 postTeam)")
    void bothTeamsAreAlwaysPresentInRequestResponse() throws Exception {
        // 글 작성자 관점 — 받은 신청
        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postTeam.name").value("FC 새벽"))
                .andExpect(jsonPath("$[0].applicantTeam.name").isNotEmpty());

        // 신청 팀 관점 — 보낸 신청. 여기가 postTeam 이 없으면 "누구에게 신청했는지"를 못 그린다
        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postTeam.name").value("FC 새벽"))
                .andExpect(jsonPath("$[0].applicantTeam.name").value("마포 유나이티드"));

        // 글별 목록
        mockMvc.perform(get("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postTeam.name").value("FC 새벽"));

        // 상태를 바꾸는 응답들도 같은 형태여야 한다
        mockMvc.perform(post("/api/requests/{id}/accept", requestB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postTeam.name").value("FC 새벽"))
                .andExpect(jsonPath("$.applicantTeam.name").value("마포 유나이티드"));

        mockMvc.perform(post("/api/requests/{id}/confirm-deposit", requestB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postTeam.name").value("FC 새벽"));
    }

    private long apply(User user, String message) throws Exception {
        return idOf(mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isCreated()));
    }

    private void accept(long requestId) throws Exception {
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());
    }
}
