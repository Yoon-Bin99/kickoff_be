package com.kickoff.be.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
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
 * 차단 (계약서 §7-2 「차단」, v1.35.0).
 *
 * <b>차단은 두 겹이다.</b> 바깥 겹은 그 사람의 화면(글·리뷰·팀 찾기에서 사라진다), 안쪽
 * 겹은 두 팀 사이(신청·채팅이 막힌다). 안쪽은 차단한 사람이 <b>감독일 때만</b> 생긴다 —
 * 팀원이 상대 팀을 차단했다고 감독끼리 잡은 경기가 깨지면 안 된다.
 *
 * 상대에게는 아무것도 알리지 않는다. 신청이 막힐 때조차 "지금은 이 팀에 신청할 수
 * 없습니다"라고만 한다.
 */
class BlockTest extends IntegrationTestSupport {

    private User me;
    private User other;
    private Team myTeam;
    private Team otherTeam;

    @BeforeEach
    void setUpTeams() {
        me = createUser("me@example.com", "김감독", "010-1111-1111");
        other = createUser("other@example.com", "박감독", "010-2222-2222");
        myTeam = createTeam(me, "FC 새벽", "서울 강서구");
        otherTeam = createTeam(other, "FC 노을", "서울 송파구");
    }

    // ── 기본

    @Test
    @DisplayName("팀을 차단하면 201 이고 목록에 쌓인다")
    void blockingTeamSucceeds() throws Exception {
        block(otherTeam.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.team.id").value(otherTeam.getId()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/blocks").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].team.name").value("FC 노을"));
    }

    /** 멱등이다 — 두 번 눌러도 차단이 둘이 되지 않는다. */
    @Test
    @DisplayName("이미 차단한 팀이면 200 으로 같은 응답을 준다")
    void blockingTwiceIsIdempotent() throws Exception {
        block(otherTeam.getId()).andExpect(status().isCreated());
        block(otherTeam.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.id").value(otherTeam.getId()));

        assertThat(blockRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("해제하면 204 이고, 차단한 적 없어도 204 다")
    void unblockIsIdempotent() throws Exception {
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/blocks/{teamId}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/blocks/{teamId}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNoContent());
        assertThat(blockRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("내 팀은 차단할 수 없고 없는 팀은 404 다")
    void cannotBlockOwnTeam() throws Exception {
        block(myTeam.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'teamId')]").exists());

        block(999999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    // ── 화면 효과 (계약서 §7-2 「차단의 효과」)

    @Test
    @DisplayName("차단한 팀의 글이 내 목록에서 빠진다 — 남에게는 그대로다")
    void blockedTeamPostsDisappear() throws Exception {
        MatchPost post = createPost(otherTeam, "토요일 아침 상대 구합니다");
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .doesNotExist());
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .exists());
    }

    @Test
    @DisplayName("팀 찾기에서도 빠진다")
    void blockedTeamIsNotFound() throws Exception {
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams").header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(otherTeam.getId()))
                        .doesNotExist());
    }

    /** 닫아 버리면 해제할 길이 사라진다 — 그래서 팀 페이지는 열고 표시만 준다. */
    @Test
    @DisplayName("차단한 팀의 페이지는 열리고 blockedByMe 가 true 다")
    void blockedTeamPageStillOpens() throws Exception {
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{id}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByMe").value(true));
        mockMvc.perform(get("/api/teams/{id}", otherTeam.getId()))
                .andExpect(jsonPath("$.blockedByMe").value(false));
    }

    @Test
    @DisplayName("차단한 팀이 쓴 리뷰가 내 화면에서 빠진다")
    void reviewsWrittenByBlockedTeamDisappear() throws Exception {
        MatchPost past = createPost(myTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest request = acceptedRequest(past, otherTeam);
        mockMvc.perform(post("/api/requests/{id}/review", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"좋았습니다.\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{id}/reviews", myTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)));

        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{id}/reviews", myTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(0)));
        // 제3자에게는 그대로 보인다 (계약서 §7-2 「하지 않는 것」)
        mockMvc.perform(get("/api/teams/{id}/reviews", myTeam.getId()))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)));
    }

    // ── 두 팀 사이 (계약서 §7-2)

    @Test
    @DisplayName("차단하면 어느 방향이든 신청이 막힌다")
    void requestsAreBlockedBothWays() throws Exception {
        MatchPost theirPost = createPost(otherTeam, "상대 구합니다");
        MatchPost myPost = createPost(myTeam, "우리도 구합니다");
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(post("/api/posts/{id}/requests", theirPost.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ALLOWED"))
                // 문구가 차단 사실을 드러내지 않는다 (계약서 §7-2)
                .andExpect(jsonPath("$.message").value("지금은 이 팀에 신청할 수 없습니다."));

        // 차단당한 쪽도 막힌다. 한쪽만 막으면 상대는 계속 보낼 수 있는데 받는 쪽
        // 화면에는 안 보이는 상태가 된다
        mockMvc.perform(post("/api/posts/{id}/requests", myPost.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ALLOWED"));
    }

    /**
     * 조율 수단만 끊고 경기를 남겨 두면 양 팀이 구장에서 만나게 된다 — 그래서 확인을
     * 받고 함께 취소한다.
     */
    @Test
    @DisplayName("예정된 매칭이 있으면 409 이고 가장 가까운 경기 시각이 실린다")
    void upcomingMatchBlocksTheBlock() throws Exception {
        MatchPost post = createPost(myTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(7), true);
        acceptedRequest(post, otherTeam);

        block(otherTeam.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCK_HAS_UPCOMING_MATCH"))
                .andExpect(jsonPath("$.matchAt").isNotEmpty());
        assertThat(blockRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("cancelUpcomingMatch 로 다시 보내면 매칭이 취소되고 차단된다")
    void cancellingUpcomingMatchAllowsBlock() throws Exception {
        MatchPost post = createPost(myTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(7), true);
        MatchRequest request = acceptedRequest(post, otherTeam);

        mockMvc.perform(post("/api/blocks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":%d,\"cancelUpcomingMatch\":true}"
                                .formatted(otherTeam.getId())))
                .andExpect(status().isCreated());

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.MATCH_CANCELED);
        // 글은 다시 모집 중으로 돌아간다 (§6-2 와 같은 취소다)
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.OPEN);
        assertThat(blockRepository.findAll()).hasSize(1);
    }

    /** 지난 경기는 취소할 것이 없다 — 막을 이유도 없다. */
    @Test
    @DisplayName("지난 매칭은 차단을 막지 않는다")
    void pastMatchDoesNotBlock() throws Exception {
        MatchPost post = createPost(myTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        acceptedRequest(post, otherTeam);

        block(otherTeam.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("대기 중인 신청은 양방향으로 정리된다")
    void pendingRequestsAreCleared() throws Exception {
        MatchPost theirPost = createPost(otherTeam, "상대 구합니다");
        MatchPost myPost = createPost(myTeam, "우리도 구합니다");
        MatchRequest sent = pendingRequest(theirPost, myTeam);
        MatchRequest received = pendingRequest(myPost, otherTeam);

        block(otherTeam.getId()).andExpect(status().isCreated());

        // 내가 보낸 것은 내가 거두는 것이고, 내 글에 온 것은 내가 거절하는 것이다
        assertThat(requestRepository.findById(sent.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.CANCELED);
        assertThat(requestRepository.findById(received.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    @DisplayName("차단 관계면 수락된 매칭의 연락처·계좌가 null 이다")
    void contactIsHiddenWhileBlocked() throws Exception {
        MatchPost post = createPost(otherTeam, "지난 경기",
                OffsetDateTime.now().minusDays(1), true);
        acceptedRequest(post, myTeam);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.contact").isNotEmpty());

        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist());
        // 신청 목록도 같은 규칙이다
        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(jsonPath("$[0].contact").doesNotExist());
    }

    /**
     * 차단·해제로 방 상태가 달라지면 채팅 서버에 알린다 (계약서 §10) — 알리지 않으면
     * 상대 화면의 입력창이 최대 15초 동안 열려 있다.
     */
    @Test
    @DisplayName("차단·해제 때 채팅 서버에 ROOM_INVALIDATED 가 쌓인다")
    void chatIsNotifiedOnBlockAndUnblock() throws Exception {
        MatchPost post = createPost(otherTeam, "지난 경기",
                OffsetDateTime.now().minusDays(1), true);
        acceptedRequest(post, myTeam);

        block(otherTeam.getId()).andExpect(status().isCreated());
        assertThat(chatOutboxRepository.findAll()).hasSize(1);

        mockMvc.perform(delete("/api/blocks/{teamId}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNoContent());
        assertThat(chatOutboxRepository.findAll()).hasSize(2);
    }

    /** 감독이 아닌 계정의 차단은 "화면" 효과만 있다 (계약서 §7-2). */
    @Test
    @DisplayName("팀 없는 계정이 차단해도 두 팀 사이는 막히지 않는다")
    void blockByNonOwnerOnlyAffectsTheirScreen() throws Exception {
        User watcher = createUserWithoutConsents("구경꾼", "010-7777-7777");
        consentService.recordSignupConsents(watcher);
        MatchPost post = createPost(otherTeam, "상대 구합니다");

        mockMvc.perform(post("/api/blocks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(watcher))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":%d}".formatted(otherTeam.getId())))
                .andExpect(status().isCreated());

        // 그 사람 화면에서만 빠진다
        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(watcher)))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .doesNotExist());
        // 감독인 내 신청은 그대로 된다
        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("해제하면 신청이 다시 된다 — 취소된 매칭은 되살아나지 않는다")
    void unblockRestoresRequests() throws Exception {
        MatchPost post = createPost(otherTeam, "상대 구합니다");
        block(otherTeam.getId()).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/blocks/{teamId}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void blockNeedsLogin() throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":%d}".formatted(otherTeam.getId())))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions block(Long teamId) throws Exception {
        return mockMvc.perform(post("/api/blocks")
                .header(HttpHeaders.AUTHORIZATION, bearer(me))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":%d}".formatted(teamId)));
    }
}
