package com.kickoff.be.user;

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
 * 이미 잡힌 경기는 이용 제한 중에도 다룰 수 있다 (계약서 §7-2, v1.39.0).
 *
 * <b>왜 여는가.</b> 막아 두면 제한된 감독이 예정된 경기를 취소하지도, 상대 연락처를
 * 보지도 못한다. 그 피해는 그 사람이 아니라 <b>상대 팀</b>이 본다 — 아무도 나오지 않는
 * 경기장에 나가게 된다. 이용 제한은 그 사람의 활동을 멈추는 것이지, 남에게 손해를
 * 입히는 수단이 아니다.
 *
 * <b>그래서 여는 것은 셋뿐이다</b> — 매칭 목록 둘과 취소 하나. 새 약속을 만드는 일
 * (새 글·새 신청·수락·거절·입금 확인)은 그대로 막힌다.
 */
class SuspendedMatchAccessTest extends IntegrationTestSupport {

    @org.springframework.beans.factory.annotation.Value("${chat.internal.secret}")
    private String internalSecret;

    private User suspended;
    private Team suspendedTeam;
    private User opponent;
    private Team opponentTeam;

    @BeforeEach
    void setUpMatch() {
        suspended = createUser("suspended@example.com", "제한계정", "010-1111-1111");
        suspendedTeam = createTeam(suspended, "FC 새벽", "서울 강서구");
        opponent = createUser("opponent@example.com", "상대", "010-2222-2222");
        opponentTeam = createTeam(opponent, "FC 노을", "서울 송파구");
        suspend(suspended);
    }

    // ── 열리는 것 세 가지

    @Test
    @DisplayName("받은 신청 목록은 제한 중에도 열린다")
    void receivedListStaysOpen() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        acceptedRequest(post, opponentTeam);

        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"))
                // 상대 연락처를 볼 수 있어야 경기를 조율하거나 사정을 알릴 수 있다
                .andExpect(jsonPath("$[0].contact").exists());
    }

    @Test
    @DisplayName("보낸 신청 목록은 제한 중에도 열린다")
    void sentListStaysOpen() throws Exception {
        MatchPost post = createPost(opponentTeam, "상대 팀 글", upcoming(), true);
        acceptedRequest(post, suspendedTeam);

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    /** 취소까지 막으면 노쇼가 되고, 그 손해는 상대 팀이 본다 (계약서 §6-2·§7-2). */
    @Test
    @DisplayName("예정된 매칭 취소는 제한 중에도 된다")
    void cancelMatchStaysOpen() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        MatchRequest request = acceptedRequest(post, opponentTeam);

        mockMvc.perform(post("/api/requests/{id}/cancel-match", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk());

        assertThat(requestRepository.findById(request.getId()).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.MATCH_CANCELED);
        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .as("글은 다시 모집 중으로 돌아간다 — 상대가 사라진 매칭완료 글이 남으면 안 된다")
                .isEqualTo(PostStatus.OPEN);
    }

    // ── 그대로 막히는 것 (새 약속을 만드는 일)

    @Test
    @DisplayName("새 신청은 막힌다")
    void newRequestIsBlocked() throws Exception {
        MatchPost post = createPost(opponentTeam, "상대 팀 글", upcoming(), true);

        suspendedBlocked(post("/api/posts/{id}/requests", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"신청합니다\"}"));
    }

    @Test
    @DisplayName("수락·거절은 막힌다")
    void acceptAndRejectAreBlocked() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        MatchRequest pending = pendingRequest(post, opponentTeam);

        suspendedBlocked(post("/api/requests/{id}/accept", pending.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended)));
        suspendedBlocked(post("/api/requests/{id}/reject", pending.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended)));
    }

    @Test
    @DisplayName("신청 철회는 막힌다 — 수락 전이라 급한 일이 아니다")
    void withdrawIsBlocked() throws Exception {
        MatchPost post = createPost(opponentTeam, "상대 팀 글", upcoming(), true);
        MatchRequest pending = pendingRequest(post, suspendedTeam);

        suspendedBlocked(delete("/api/requests/{id}", pending.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended)));
    }

    @Test
    @DisplayName("입금 확인은 막힌다")
    void confirmDepositIsBlocked() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        MatchRequest accepted = acceptedRequest(post, opponentTeam);

        suspendedBlocked(post("/api/requests/{id}/confirm-deposit", accepted.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended)));
    }

    @Test
    @DisplayName("새 글 쓰기는 막힌다")
    void writingAPostIsBlocked() throws Exception {
        suspendedBlocked(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"새 글","content":"내용입니다.",
                         "matchAt":"%s","location":"우장산축구장","region":"서울 강서구",
                         "preferredSkillLevel":"INTERMEDIATE"}
                        """.formatted(upcoming())));
    }

    /**
     * 예외는 <b>경로 한 토막</b>만 받는다 — 접두어로 열면 그 아래 다른 경로가 함께
     * 열린다. 열린 경로 밑에 다른 것이 없는지 한 번 밟아 둔다.
     */
    @Test
    @DisplayName("열린 경로의 이름만 닮은 다른 경로는 열리지 않는다")
    void onlyTheExactPathsAreOpen() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        MatchRequest accepted = acceptedRequest(post, opponentTeam);

        // /api/posts/{id}/requests — "requests" 로 끝나지만 예외가 아니다
        suspendedBlocked(get("/api/posts/{id}/requests", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended)));
        // 취소 경로의 한 토막 자리에 다른 경로를 끼워도 맞지 않는다
        suspendedBlocked(post("/api/requests/{id}/review", accepted.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(suspended))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5,\"comment\":\"좋았습니다.\"}"));
    }

    // ── 검사 순서

    /**
     * 동의가 먼저다 (계약서 §7-2). 새로 연 경로에서도 순서가 같아야 한다 — 뒤집히면
     * 동의도 안 한 계정이 "제한됐다"는 안내를 먼저 받는다.
     */
    @Test
    @DisplayName("동의 대기와 제한이 겹치면 CONSENT_REQUIRED 가 먼저다")
    void consentIsStillCheckedFirst() throws Exception {
        User pending = createUserWithoutConsents("동의안함", "010-3333-3333");
        suspend(pending);

        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("토큰이 없으면 403 이 아니라 401 이다")
    void unauthenticatedStillGets401() throws Exception {
        mockMvc.perform(get("/api/requests/received"))
                .andExpect(status().isUnauthorized());
    }

    // ── 채팅 서버 (계약서 §10)

    /**
     * 채팅 서버는 이용 제한을 보지 않는다 (계약서 §10·§7-2).
     *
     * 제한된 감독의 방 목록과 방 조회가 그대로 답해야 한다 — 그러지 않으면 상대 팀의
     * 채팅까지 멈춘다(방은 둘이 쓰는 것이다). 경기 취소를 열어 둔 것과 같은 생각이다.
     *
     * { /internal/**} 은 시큐리티에서 ROLE_INTERNAL 로 먼저 걸러지므로 이용 제한
     * 게이트까지 오지 않는다. 그 성질을 여기서 밟아 둔다.
     */
    @Test
    @DisplayName("제한 중에도 채팅 서버의 방 조회는 그대로 답한다")
    void internalChatIgnoresSuspension() throws Exception {
        MatchPost post = createPost(suspendedTeam, "토요일 아침", upcoming(), true);
        MatchRequest accepted = acceptedRequest(post, opponentTeam);

        mockMvc.perform(get("/internal/chat/rooms/{id}", accepted.getId())
                        .header(com.kickoff.be.chatlink.security.InternalSecretFilter.HEADER,
                                internalSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(accepted.getId()));

        mockMvc.perform(get("/internal/chat/users/{id}/rooms", suspended.getId())
                        .header(com.kickoff.be.chatlink.security.InternalSecretFilter.HEADER,
                                internalSecret))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(accepted.getId()));
    }

    // ── 헬퍼

    private void suspendedBlocked(org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        ResultActions result = mockMvc.perform(request);
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    private static OffsetDateTime upcoming() {
        return OffsetDateTime.now().plusDays(7);
    }

    private void suspend(User user) {
        transactionTemplate.executeWithoutResult(status -> userRepository
                .findById(user.getId()).orElseThrow()
                .suspendUntil(OffsetDateTime.now().plusDays(7)));
    }
}
