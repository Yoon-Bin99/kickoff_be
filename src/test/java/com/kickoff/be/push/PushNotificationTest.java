package com.kickoff.be.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 푸시 알림 (계약서 §8). 발송은 스텁으로 받고, 누구에게 무엇이 나갔는지를 본다.
 *
 * 여기서 검증하는 성질 중 가장 중요한 건 <b>푸시가 원 요청을 망가뜨리지 않는다</b>는 것이다.
 * 알림이 실패했다고 이미 성사된 매칭을 무를 수는 없다.
 */
class PushNotificationTest extends IntegrationTestSupport {

    private static final String TOKEN_A = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";
    private static final String TOKEN_B = "ExponentPushToken[bbbbbbbbbbbbbbbbbbbbbb]";
    private static final String TOKEN_C = "ExponentPushToken[cccccccccccccccccccccc]";

    private User author;
    private User applicantB;
    private User applicantC;
    private MatchPost post;

    @BeforeEach
    void setUpMatch() throws Exception {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicantB = createUser("b@example.com", "이감독", "010-2222-2222");
        applicantC = createUser("c@example.com", "박캡틴", "010-3333-3333");

        Team authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(applicantB, "마포 유나이티드", "서울 마포구");
        createTeam(applicantC, "송파 FC", "서울 송파구");

        post = createPost(authorTeam, "토요일 아침 풋살 상대 구합니다");

        registerToken(author, TOKEN_A);
        registerToken(applicantB, TOKEN_B);
        registerToken(applicantC, TOKEN_C);
        pushClient.reset();
    }

    // ── 토큰 등록 (계약서 §8)

    @Test
    @DisplayName("토큰을 등록하면 204, 같은 값을 다시 넣어도 204 — 멱등이다")
    void registeringTokenIsIdempotent() throws Exception {
        putToken(applicantB, TOKEN_B).andExpect(status().isNoContent());
        putToken(applicantB, TOKEN_B).andExpect(status().isNoContent());

        assertThat(userRepository.findById(applicantB.getId()).orElseThrow().getExpoPushToken())
                .isEqualTo(TOKEN_B);
    }

    @Test
    @DisplayName("마지막 등록이 이긴다 — 기기를 바꾸면 새 기기만 받는다")
    void lastRegistrationWins() throws Exception {
        putToken(applicantB, TOKEN_C).andExpect(status().isNoContent());

        assertThat(userRepository.findById(applicantB.getId()).orElseThrow().getExpoPushToken())
                .isEqualTo(TOKEN_C);
    }

    @Test
    @DisplayName("null 을 보내면 등록 해제된다 — FE 가 로그아웃할 때 부른다")
    void nullTokenUnregisters() throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":null}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(applicantB.getId()).orElseThrow().hasPushToken())
                .isFalse();
    }

    @Test
    @DisplayName("Expo 형식이 아니면 400 VALIDATION_FAILED")
    void malformedTokenIsRejected() throws Exception {
        putToken(applicantB, "not-a-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("expoPushToken"));
    }

    @Test
    @DisplayName("비로그인은 토큰을 등록할 수 없다 — 401")
    void anonymousCannotRegisterToken() throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"" + TOKEN_B + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 이벤트 4종 (계약서 §8)

    @Test
    @DisplayName("신청이 들어오면 글 작성 팀에게 REQUEST_RECEIVED 가 간다")
    void requestReceivedGoesToPostAuthor() throws Exception {
        apply(applicantB, "마포에서 갑니다");

        assertThat(pushClient.sent()).hasSize(1);
        PushMessage sent = pushClient.last();
        assertThat(sent.to()).isEqualTo(TOKEN_A);
        assertThat(sent.title()).isEqualTo("새 매칭 신청");
        assertThat(sent.body())
                .isEqualTo("마포 유나이티드이(가) '토요일 아침 풋살 상대 구합니다'에 신청했습니다");
        assertThat(sent.data().get("type")).isEqualTo("REQUEST_RECEIVED");
        assertThat(sent.data().get("postId")).isEqualTo(post.getId());
        assertThat(sent.data().get("requestId")).isNotNull();
    }

    @Test
    @DisplayName("수락하면 신청 팀에게 REQUEST_ACCEPTED 가 간다")
    void acceptNotifiesApplicant() throws Exception {
        long requestId = apply(applicantB, "마포에서 갑니다");
        pushClient.reset();

        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        assertThat(pushClient.sent()).hasSize(1);
        PushMessage sent = pushClient.last();
        assertThat(sent.to()).isEqualTo(TOKEN_B);
        assertThat(sent.title()).isEqualTo("매칭 성사!");
        assertThat(sent.body())
                .isEqualTo("'토요일 아침 풋살 상대 구합니다' 신청이 수락됐습니다. 연락처가 공개됐어요");
        assertThat(sent.data().get("type")).isEqualTo("REQUEST_ACCEPTED");
    }

    @Test
    @DisplayName("수락하면 자동 거절된 팀에게도 REQUEST_REJECTED 가 간다")
    void acceptAlsoNotifiesAutoRejected() throws Exception {
        long requestB = apply(applicantB, "마포에서 갑니다");
        apply(applicantC, "송파에서 갑니다");
        pushClient.reset();

        mockMvc.perform(post("/api/requests/{id}/accept", requestB)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        // 수락된 마포에 ACCEPTED, 자동 거절된 송파에 REJECTED — 두 건이 나가야 한다
        assertThat(pushClient.sent()).hasSize(2);
        PushMessage accepted = find(TOKEN_B);
        PushMessage rejected = find(TOKEN_C);
        assertThat(accepted.data().get("type")).isEqualTo("REQUEST_ACCEPTED");
        assertThat(rejected.data().get("type")).isEqualTo("REQUEST_REJECTED");
        assertThat(rejected.title()).isEqualTo("매칭 불발");
        assertThat(rejected.body()).isEqualTo("'토요일 아침 풋살 상대 구합니다' 신청이 거절됐습니다");
    }

    @Test
    @DisplayName("거절하면 신청 팀에게 REQUEST_REJECTED 가 간다")
    void rejectNotifiesApplicant() throws Exception {
        long requestId = apply(applicantB, "마포에서 갑니다");
        pushClient.reset();

        mockMvc.perform(post("/api/requests/{id}/reject", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        assertThat(pushClient.sent()).hasSize(1);
        assertThat(pushClient.last().to()).isEqualTo(TOKEN_B);
        assertThat(pushClient.last().data().get("type")).isEqualTo("REQUEST_REJECTED");
    }

    @Test
    @DisplayName("입금을 확인하면 신청 팀에게 DEPOSIT_CONFIRMED 가 간다")
    void depositConfirmNotifiesApplicant() throws Exception {
        long requestId = apply(applicantB, "마포에서 갑니다");
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(author)));
        pushClient.reset();

        mockMvc.perform(post("/api/requests/{id}/confirm-deposit", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        assertThat(pushClient.sent()).hasSize(1);
        assertThat(pushClient.last().to()).isEqualTo(TOKEN_B);
        assertThat(pushClient.last().title()).isEqualTo("입금 확인");
        assertThat(pushClient.last().body())
                .isEqualTo("'토요일 아침 풋살 상대 구합니다' 입금이 확인됐습니다");
        assertThat(pushClient.last().data().get("type")).isEqualTo("DEPOSIT_CONFIRMED");
    }

    // ── 안 보내는 경우

    @Test
    @DisplayName("토큰이 없는 사용자에게는 조용히 건너뛴다 — 매칭 흐름은 그대로 돈다")
    void skipsUsersWithoutToken() throws Exception {
        // 글 작성자가 로그아웃해 토큰을 지운 상태
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":null}"))
                .andExpect(status().isNoContent());
        pushClient.reset();

        long requestId = apply(applicantB, "마포에서 갑니다");

        assertThat(pushClient.sent()).isEmpty();
        // 알림이 없어도 신청 자체는 정상이다
        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.PENDING);
    }

    @Test
    @DisplayName("발송이 터져도 원 요청은 성공한다 — 푸시는 best-effort 다")
    void pushFailureDoesNotBreakTheRequest() throws Exception {
        long requestId = apply(applicantB, "마포에서 갑니다");
        pushClient.reset();
        pushClient.willFail();

        // 수락 응답은 정상이어야 하고, 상태 전이도 그대로 일어나야 한다
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(requestRepository.findById(requestId).orElseThrow().getStatus())
                .isEqualTo(RequestStatus.ACCEPTED);
        assertThat(pushClient.sent()).isEmpty();
    }

    @Test
    @DisplayName("취소는 알림을 만들지 않는다 — 계약서 이벤트 4종에 없다")
    void cancelSendsNothing() throws Exception {
        long requestId = apply(applicantB, "마포에서 갑니다");
        pushClient.reset();

        mockMvc.perform(delete("/api/requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantB)))
                .andExpect(status().isNoContent());

        assertThat(pushClient.sent()).isEmpty();
    }

    private PushMessage find(String token) {
        List<PushMessage> matches = pushClient.sent().stream()
                .filter(m -> m.to().equals(token))
                .toList();
        assertThat(matches).as("토큰 %s 로 나간 알림", token).hasSize(1);
        return matches.get(0);
    }

    private void registerToken(User user, String token) throws Exception {
        putToken(user, token).andExpect(status().isNoContent());
    }

    private org.springframework.test.web.servlet.ResultActions putToken(User user, String token)
            throws Exception {
        return mockMvc.perform(put("/api/users/me/push-token")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expoPushToken\":\"" + token + "\"}"));
    }

    private long apply(User user, String message) throws Exception {
        return idOf(mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isCreated()));
    }
}
