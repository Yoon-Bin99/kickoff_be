package com.kickoff.be.matchrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.push.dto.PushMessage;
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
 * 매칭 취소 (계약서 §6-2, v1.20.0).
 *
 * 이 기능의 위험은 <b>취소가 무엇을 닫는지</b>에 있다. 상태만 바뀌고 연락처·채팅이 열린 채
 * 남으면, 취소한 팀의 전화번호가 상대에게 계속 보인다 — 화면은 멀쩡하고 에러도 없다.
 *
 * 구현은 그것들을 하나씩 닫지 않는다. 전부 {@code isAccepted()} 를 통과하게 돼 있어서
 * 상태가 ACCEPTED 가 아니게 되면 함께 닫힌다. <b>그 구조가 실제로 성립하는지</b>를 여기서
 * 하나씩 확인한다 — 믿고 넘어가면 나중에 누가 어느 하나를 다른 조건으로 바꿔도 모른다.
 */
class MatchCancelTest extends IntegrationTestSupport {

    private static final String TOKEN_AUTHOR = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";
    private static final String TOKEN_B = "ExponentPushToken[bbbbbbbbbbbbbbbbbbbbbb]";
    private static final String TOKEN_C = "ExponentPushToken[cccccccccccccccccccccc]";

    private User author;
    private User applicantB;
    private User applicantC;
    private User stranger;
    private Team authorTeam;
    private Team teamB;
    private MatchPost post;
    private long acceptedId;
    private long autoRejectedId;

    @BeforeEach
    void setUpAcceptedMatch() throws Exception {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicantB = createUser("b@example.com", "이감독", "010-2222-2222");
        applicantC = createUser("c@example.com", "박캡틴", "010-3333-3333");
        stranger = createUser("x@example.com", "최무관", "010-9999-9999");

        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        teamB = createTeam(applicantB, "마포 유나이티드", "서울 마포구");
        createTeam(applicantC, "송파 FC", "서울 송파구");
        createTeam(stranger, "무관 FC", "인천 남동구");

        post = createPost(authorTeam, "토요일 아침 11인제 상대 구합니다");
        acceptedId = applyTo(post, applicantB);
        autoRejectedId = applyTo(post, applicantC);

        // B 를 수락하면 C 는 자동 거절되고 글은 MATCHED 가 된다.
        mockMvc.perform(post("/api/requests/" + acceptedId + "/accept")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        registerToken(author, TOKEN_AUTHOR);
        registerToken(applicantB, TOKEN_B);
        registerToken(applicantC, TOKEN_C);
        pushClient.reset();
    }

    // ── 권한 (계약서 §6-2 — 양 팀 어느 쪽이든)

    @Test
    @DisplayName("글 작성 팀 주장이 취소할 수 있다")
    void authorCanCancel() throws Exception {
        cancel(author).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCH_CANCELED"));
    }

    @Test
    @DisplayName("신청 팀 주장도 취소할 수 있다 — 한쪽만 되면 상대는 잡혀 있게 된다")
    void applicantCanCancel() throws Exception {
        cancel(applicantB).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCH_CANCELED"));
    }

    @Test
    @DisplayName("제3자는 403")
    void strangerForbidden() throws Exception {
        cancel(stranger).andExpect(status().isForbidden());
        assertThat(statusOf(acceptedId)).isEqualTo(RequestStatus.ACCEPTED);
    }

    @Test
    @DisplayName("자동 거절된 팀도 남의 매칭은 못 건드린다 — 같은 글에 얽혀 있어도 당사자가 아니다")
    void autoRejectedTeamForbidden() throws Exception {
        cancel(applicantC).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("미인증은 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(post("/api/requests/" + acceptedId + "/cancel-match"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("권한을 상태보다 먼저 본다 — 제3자에게는 409 가 아니라 403")
    void authorizationCheckedBeforeState() throws Exception {
        // 순서를 뒤집으면 제3자가 403/409 의 차이로 "그 신청이 수락된 상태인가"를 알아낸다.
        // 수락 안 된 신청에 제3자가 부르면 409 가 아니라 403 이어야 한다.
        long pending = applyTo(createPost(authorTeam, "다른 글"), applicantB);
        mockMvc.perform(post("/api/requests/" + pending + "/cancel-match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    // ── 상태 조건

    @Test
    @DisplayName("수락되지 않은 신청은 409 REQUEST_NOT_ACCEPTED")
    void pendingCannotBeCanceled() throws Exception {
        long pending = applyTo(createPost(authorTeam, "다른 글"), applicantB);
        mockMvc.perform(post("/api/requests/" + pending + "/cancel-match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
    }

    @Test
    @DisplayName("멱등이 아니다 — 이미 취소된 매칭에 또 부르면 409")
    void cancelingTwiceConflicts() throws Exception {
        cancel(author).andExpect(status().isOk());
        cancel(author).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
    }

    @Test
    @DisplayName("경기 시각이 지났으면 409 MATCH_CANCEL_EXPIRED — 그건 리뷰·전적의 영역이다")
    void pastMatchCannotBeCanceled() throws Exception {
        MatchPost past = createPost(authorTeam, "지난 경기",
                OffsetDateTime.now().minusDays(1), true);
        MatchRequest accepted = acceptedRequest(past, teamB);

        mockMvc.perform(post("/api/requests/" + accepted.getId() + "/cancel-match")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_CANCEL_EXPIRED"));
    }

    // ── 효과

    @Test
    @DisplayName("글이 OPEN 으로 돌아오고 다시 신청을 받는다")
    void postReopens() throws Exception {
        cancel(author).andExpect(status().isOk());

        assertThat(postRepository.findById(post.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.OPEN);
        // 취소의 목적이 이것이다 — 글을 되살려 다른 팀을 구하는 것
        mockMvc.perform(post("/api/posts/" + post.getId() + "/requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"저희가 가겠습니다\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("자동 거절됐던 신청은 REJECTED 그대로 — 되살아나지 않는다")
    void autoRejectedStaysRejected() throws Exception {
        cancel(author).andExpect(status().isOk());

        // 되살리면 거절 통보를 받은 팀이 영문도 모르고 다시 매칭 후보가 된다
        assertThat(statusOf(autoRejectedId)).isEqualTo(RequestStatus.REJECTED);
    }

    @Test
    @DisplayName("거절됐던 팀도, 취소된 팀도 다시 신청할 수 있다")
    void bothSidesCanReapply() throws Exception {
        cancel(author).andExpect(status().isOk());

        // 중복 신청 판정은 PENDING/ACCEPTED 만 본다. MATCH_CANCELED 를 거기 넣으면
        // 취소된 팀이 영영 재신청을 못 하게 되는데, 에러 메시지는 "이미 신청한 글입니다"라
        // 왜 막히는지 알 수 없다.
        reapply(applicantC).andExpect(status().isCreated());
        reapply(applicantB).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("입금 기록은 지우지 않는다 — 환불 분쟁의 유일한 근거다")
    void depositRecordSurvives() throws Exception {
        mockMvc.perform(post("/api/requests/" + acceptedId + "/confirm-deposit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        cancel(author).andExpect(status().isOk());

        assertThat(requestRepository.findById(acceptedId).orElseThrow().isDepositPaid())
                .isTrue();
    }

    // ── 취소가 닫는 것들 (전부 isAccepted() 를 통과한다)

    @Test
    @DisplayName("연락처와 계좌가 다시 가려진다")
    void contactAndPaymentGoPrivate() throws Exception {
        // 취소 전에는 보인다
        sentRequests(applicantB).andExpect(jsonPath("$[0].contact").exists());

        cancel(author).andExpect(status().isOk());

        sentRequests(applicantB)
                .andExpect(jsonPath("$[0].contact").doesNotExist())
                .andExpect(jsonPath("$[0].payment").doesNotExist());
    }

    @Test
    @DisplayName("채팅이 닫히고 방 목록에서도 사라진다")
    void chatCloses() throws Exception {
        mockMvc.perform(get("/api/requests/" + acceptedId + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(status().isOk());

        cancel(author).andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/" + acceptedId + "/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
        // 목록에 남아 있으면 눌렀을 때만 막히는 방이 보인다
        mockMvc.perform(get("/api/users/me/chats")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("입금 확인을 더 받지 않는다")
    void depositCannotBeConfirmedAfterCancel() throws Exception {
        cancel(author).andExpect(status().isOk());

        mockMvc.perform(post("/api/requests/" + acceptedId + "/confirm-deposit")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
    }

    @Test
    @DisplayName("경기 시각이 지나도 리뷰·전적을 쓸 수 없다 — 열리지 않은 채로 지나간다")
    void canceledMatchNeverBecomesReviewable() throws Exception {
        // 취소는 경기 전에만 되지만, 그 경기 시각은 결국 지나간다. 그때 리뷰 조건
        // ("끝난 경기의 수락된 매칭")의 앞쪽 절반이 충족되므로 뒤쪽 절반이 막고 있어야 한다.
        // 안 그러면 하지도 않은 경기에 평점이 붙는다.
        MatchPost past = createPost(authorTeam, "취소된 뒤 지나간 경기",
                OffsetDateTime.now().minusDays(1), true);
        MatchRequest canceled = acceptedRequest(past, teamB);
        canceled.cancelMatch();
        requestRepository.save(canceled);

        mockMvc.perform(post("/api/requests/" + canceled.getId() + "/review")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"좋았습니다\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/requests/" + canceled.getId() + "/record")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ourScore\":3,\"opponentScore\":1}"))
                .andExpect(status().isConflict());
    }

    // ── 목록·상세에 비치는 모습

    @Test
    @DisplayName("myRequestStatus 는 MATCH_CANCELED 를 보여준다")
    void myRequestStatusShowsCancel() throws Exception {
        cancel(author).andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/" + post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(jsonPath("$.myRequestStatus").value("MATCH_CANCELED"));
    }

    @Test
    @DisplayName("requestCount 에서 빠진다 — 살아 있는 신청이 아니다")
    void canceledIsNotCounted() throws Exception {
        cancel(author).andExpect(status().isOk());

        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[0].requestCount").value(0));
    }

    // ── 푸시 (계약서 §8)

    @Test
    @DisplayName("작성 팀이 취소하면 신청 팀에게 간다")
    void pushGoesToApplicantWhenAuthorCancels() throws Exception {
        cancel(author).andExpect(status().isOk());

        assertThat(pushClient.sent()).hasSize(1);
        PushMessage sent = pushClient.last();
        assertThat(sent.to()).isEqualTo(TOKEN_B);
        assertThat(sent.title()).isEqualTo("매칭 취소");
        assertThat(sent.data()).containsEntry("type", "MATCH_CANCELED");
    }

    @Test
    @DisplayName("신청 팀이 취소하면 작성 팀에게 간다 — 수신자가 고정이면 자기가 자기 알림을 받는다")
    void pushGoesToAuthorWhenApplicantCancels() throws Exception {
        cancel(applicantB).andExpect(status().isOk());

        assertThat(pushClient.sent()).hasSize(1);
        // 수신자를 신청 팀으로 굳혀 두면 여기서 TOKEN_B 가 나온다 — 취소한 본인이다.
        // 그러면 작성 팀은 매칭이 깨진 걸 모른 채 경기장에 나간다.
        assertThat(pushClient.last().to()).isEqualTo(TOKEN_AUTHOR);
    }

    // ── 헬퍼

    private ResultActions cancel(User who) throws Exception {
        return mockMvc.perform(post("/api/requests/" + acceptedId + "/cancel-match")
                .header(HttpHeaders.AUTHORIZATION, bearer(who)));
    }

    private long applyTo(MatchPost target, User who) throws Exception {
        return idOf(mockMvc.perform(post("/api/posts/" + target.getId() + "/requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"신청합니다\"}")));
    }

    private ResultActions reapply(User who) throws Exception {
        return mockMvc.perform(post("/api/posts/" + post.getId() + "/requests")
                .header(HttpHeaders.AUTHORIZATION, bearer(who))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"다시 신청합니다\"}"));
    }

    private ResultActions sentRequests(User who) throws Exception {
        return mockMvc.perform(get("/api/requests/sent")
                .header(HttpHeaders.AUTHORIZATION, bearer(who)));
    }

    private RequestStatus statusOf(long requestId) {
        return requestRepository.findById(requestId).orElseThrow().getStatus();
    }

    private void registerToken(User user, String token) throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + token + "\"}"))
                .andExpect(status().isNoContent());
    }
}
