package com.kickoff.be.chatlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.chat.entity.ChatLeave;
import com.kickoff.be.chatlink.security.InternalSecretFilter;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 채팅 서버 전용 내부 API (계약서 §10, v1.31.0).
 *
 * 둘로 나뉜다. <b>누가 들어올 수 있는가</b>(공유 비밀 하나뿐이고 사용자 토큰은 아무
 * 소용이 없다)와 <b>무엇을 돌려주는가</b>(판정하지 않고 사실만 — 상태로 거르지 않는다)다.
 *
 * 비밀값 자체가 비어 있을 때 전부 막히는지는 {@code InternalSecretMissingTest} 가 본다 —
 * 프로퍼티를 덮어써야 해서 컨텍스트를 따로 띄운다.
 */
class InternalChatApiTest extends IntegrationTestSupport {

    @Value("${chat.internal.secret}")
    private String secret;

    private User postOwner;
    private User applicantOwner;
    private User outsider;
    private Team postTeam;
    private Team applicantTeam;
    private MatchPost post;

    @BeforeEach
    void setUpRoom() {
        postOwner = createUser("post-owner@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        outsider = createUser("outsider@example.com", "정주장", "010-3333-3333");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");
        post = createPost(postTeam, "토요일 아침 상대 구합니다");
    }

    // ── 자격 (계약서 §10 "없거나 틀리면 401")

    @Test
    @DisplayName("비밀 헤더가 없으면 401 이다")
    void withoutSecretIsUnauthorized() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        mockMvc.perform(get("/internal/chat/rooms/{id}", accepted.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비밀 헤더가 틀리면 401 이다")
    void wrongSecretIsUnauthorized() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        mockMvc.perform(get("/internal/chat/rooms/{id}", accepted.getId())
                        .header(InternalSecretFilter.HEADER, secret + "-wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /**
     * 사용자 토큰은 이 경로와 아무 상관이 없다. 403 이 아니라 401 인 것까지 본다 —
     * 403 이면 "맞는 길인데 권한만 모자라다"로 읽혀서, 밖에서 내부 API 의 존재를 떠볼 수 있다.
     */
    @Test
    @DisplayName("JWT 만 있고 비밀 헤더가 없으면 401 이다")
    void userTokenAloneIsUnauthorized() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        mockMvc.perform(get("/internal/chat/rooms/{id}", accepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("비밀 헤더와 사용자 토큰이 함께 와도 통과한다 — 토큰이 자격을 덮지 않는다")
    void secretWinsOverUserToken() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        mockMvc.perform(get("/internal/chat/rooms/{id}", accepted.getId())
                        .header(InternalSecretFilter.HEADER, secret)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("푸시 요청도 비밀 헤더가 없으면 401 이고, 아무것도 나가지 않는다")
    void pushWithoutSecretIsUnauthorized() throws Exception {
        mockMvc.perform(post("/internal/chat/push")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pushBody(applicantOwner.getId(), 1L)))
                .andExpect(status().isUnauthorized());
        assertThat(pushClient.sent()).isEmpty();
    }

    // ── 방 하나 (계약서 §10 GET /internal/chat/rooms/{requestId})

    @Test
    @DisplayName("방 하나를 계약서의 RoomContext 모양으로 돌려준다")
    void roomHasEveryContractField() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);

        mockMvc.perform(internalGet("/internal/chat/rooms/" + accepted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(accepted.getId()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.postId").value(post.getId()))
                .andExpect(jsonPath("$.postTitle").value("토요일 아침 상대 구합니다"))
                .andExpect(jsonPath("$.matchAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.postTeam.ownerUserId").value(postOwner.getId()))
                .andExpect(jsonPath("$.postTeam.team.id").value(postTeam.getId()))
                .andExpect(jsonPath("$.postTeam.team.name").value("FC 새벽"))
                // 평점은 TeamSummary 의 일부다 (§2) — 방 목록의 otherTeam 으로 그대로 나간다
                .andExpect(jsonPath("$.postTeam.team.reviewCount").value(0))
                .andExpect(jsonPath("$.postTeam.team.averageRating").doesNotExist())
                .andExpect(jsonPath("$.applicantTeam.ownerUserId").value(applicantOwner.getId()))
                .andExpect(jsonPath("$.applicantTeam.team.id").value(applicantTeam.getId()));
    }

    /**
     * 날짜는 <b>오프셋을 붙여</b> 나간다 (계약서 §0). 기본 설정의 Jackson 이면 UTC 로 바꿔
     * `…Z` 를 내보내는데, 우리 매퍼는 `adjust-dates-to-context-time-zone: false` 라 객체가
     * 들고 있는 오프셋을 그대로 쓴다.
     *
     * 채팅 서버가 이 값을 역직렬화해 방 화면의 경기 시각으로 쓰고, `matchAt` 이 지났는지로
     * 입력창을 잠근다. 설정 한 줄이 바뀌어 Z 로 나가도 <b>가리키는 순간은 같아서</b> 테스트가
     * 없으면 아무도 모르다가, 시각을 그대로 찍는 화면에서 9시간 어긋난 값이 보인다.
     */
    @Test
    @DisplayName("날짜는 +09:00 오프셋을 달고 나간다 — UTC 로 바뀌지 않는다")
    void datesKeepTheirOffset() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);

        String body = bodyOf(mockMvc.perform(internalGet("/internal/chat/rooms/" + accepted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchAt").value(endsWith("+09:00")))
                .andExpect(jsonPath("$.updatedAt").value(endsWith("+09:00"))));

        assertThat(body).as("UTC 로 바뀌면 Z 로 끝난다").doesNotContain("Z\"");
    }

    /**
     * 계약서가 "신청 상태와 상관없이 돌려준다"고 못박은 지점이다. 여기서 걸러 버리면
     * 채팅 서버가 §6-1 의 검사 순서(권한 먼저)를 지킬 수 없다 — 403 이어야 할 자리에
     * 404 가 나가 제3자에게 매칭 상태가 새어 나간다.
     */
    @Test
    @DisplayName("수락 전 신청도 200 으로 돌려준다 — 상태로 거르지 않는다")
    void pendingRequestIsStillReturned() throws Exception {
        MatchRequest pending = pendingRequest(post, applicantTeam);

        mockMvc.perform(internalGet("/internal/chat/rooms/" + pending.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("취소된 매칭도 200 으로 돌려준다")
    void canceledMatchIsStillReturned() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        transactionTemplate.executeWithoutResult(status -> requestRepository
                .findById(accepted.getId()).orElseThrow().cancelMatch());

        mockMvc.perform(internalGet("/internal/chat/rooms/" + accepted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCH_CANCELED"));
    }

    @Test
    @DisplayName("없는 신청은 404 REQUEST_NOT_FOUND 다")
    void unknownRequestIsNotFound() throws Exception {
        mockMvc.perform(internalGet("/internal/chat/rooms/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    // ── 방 목록 (계약서 §10 GET /internal/chat/users/{userId}/rooms)

    @Test
    @DisplayName("방 목록은 그 사람이 주장인 팀의 ACCEPTED 신청뿐이다 — 양쪽에서 다 보인다")
    void roomsAreAcceptedOnlyAndSeenFromBothSides() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        // 다른 글에 걸린 대기 중 신청은 방이 아니다
        MatchPost another = createPost(postTeam, "일요일 저녁 상대 구합니다");
        pendingRequest(another, applicantTeam);

        mockMvc.perform(internalGet("/internal/chat/users/" + postOwner.getId() + "/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(accepted.getId()));

        mockMvc.perform(internalGet("/internal/chat/users/" + applicantOwner.getId() + "/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(accepted.getId()));
    }

    @Test
    @DisplayName("방이 없는 사람은 빈 배열이다")
    void userWithoutRoomsIsEmpty() throws Exception {
        mockMvc.perform(internalGet("/internal/chat/users/" + outsider.getId() + "/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /**
     * 404 가 아니다. 탈퇴 직후에도 발급해 둔 토큰은 만료까지 살아 있어서, 채팅 서버가
     * 그 토큰으로 방 목록을 물어 오는 일이 정상 경로에 있다.
     */
    @Test
    @DisplayName("없는 사용자도 404 가 아니라 빈 배열이다")
    void unknownUserIsEmptyNotNotFound() throws Exception {
        mockMvc.perform(internalGet("/internal/chat/users/99999/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ── 푸시 (계약서 §10 POST /internal/chat/push)

    @Test
    @DisplayName("푸시는 202 로 받고, 기존 채팅 푸시 모양 그대로 나간다")
    void pushIsAcceptedAndSent() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        givePushToken(applicantOwner);

        mockMvc.perform(internalPush(pushBody(applicantOwner.getId(), accepted.getId())))
                .andExpect(status().isAccepted());

        PushMessage sent = awaitPush();
        assertThat(sent.to()).isEqualTo("ExponentPushToken[applicant]");
        // title 은 보낸 팀 이름, body 는 내용 앞 50자 (§6-1)
        assertThat(sent.title()).isEqualTo("FC 새벽");
        assertThat(sent.body()).isEqualTo("그럼 7시까지 뵙겠습니다!");
        assertThat(sent.data()).containsEntry("type", "CHAT_MESSAGE");
        assertThat(sent.data()).containsEntry("requestId", accepted.getId());
        assertThat(sent.data()).containsEntry("postId", post.getId());
    }

    /**
     * BE 는 "보낼지 말지"를 판단하지 않는다 (계약서 §10) — 상대가 나갔는지, 지금 소켓으로
     * 보고 있는지는 채팅 서버만 아는 사실이라 그쪽이 정한 뒤 부른다. 그래서 나가기
     * 기록이 있어도 그대로 나간다.
     */
    @Test
    @DisplayName("BE 는 나가기 검사를 다시 하지 않는다 — 받을지는 채팅 서버가 이미 정했다")
    void pushDoesNotRecheckLeaveState() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        givePushToken(applicantOwner);
        chatLeaveRepository.save(ChatLeave.of(accepted, applicantTeam));

        mockMvc.perform(internalPush(pushBody(applicantOwner.getId(), accepted.getId())))
                .andExpect(status().isAccepted());

        assertThat(awaitPush()).isNotNull();
    }

    @Test
    @DisplayName("푸시 토큰이 없으면 조용히 버린다 — 그래도 202 다")
    void pushWithoutTokenIsStillAccepted() throws Exception {
        MatchRequest accepted = acceptedRequest(post, applicantTeam);

        mockMvc.perform(internalPush(pushBody(applicantOwner.getId(), accepted.getId())))
                .andExpect(status().isAccepted());
    }

    @Test
    @DisplayName("본문이 모자라면 400 VALIDATION_FAILED 다")
    void invalidPushBodyIsRejected() throws Exception {
        mockMvc.perform(internalPush("{\"requestId\":1,\"postId\":1,\"senderTeamName\":\"FC 새벽\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("내용이 공백만이면 400 이다")
    void blankContentIsRejected() throws Exception {
        mockMvc.perform(internalPush("{\"recipientUserId\":1,\"requestId\":1,\"postId\":1,"
                        + "\"senderTeamName\":\"FC 새벽\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private void givePushToken(User user) {
        user.updatePushToken("ExponentPushToken[applicant]");
        userRepository.save(user);
    }

    private RequestBuilder internalGet(String path) {
        return get(path).header(InternalSecretFilter.HEADER, secret);
    }

    private RequestBuilder internalPush(String body) {
        return post("/internal/chat/push")
                .header(InternalSecretFilter.HEADER, secret)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String pushBody(Long recipientUserId, Long requestId) {
        return ("{\"recipientUserId\":%d,\"requestId\":%d,\"postId\":%d,"
                + "\"senderTeamName\":\"FC 새벽\",\"content\":\"그럼 7시까지 뵙겠습니다!\"}")
                .formatted(recipientUserId, requestId, post.getId());
    }

    /** 발송은 @Async 라 응답보다 늦을 수 있다. 짧게 기다렸다가 본다. */
    private PushMessage awaitPush() throws InterruptedException {
        for (int attempt = 0; attempt < 50 && pushClient.last() == null; attempt++) {
            Thread.sleep(20);
        }
        PushMessage last = pushClient.last();
        assertThat(last).as("푸시가 나가야 한다").isNotNull();
        return last;
    }
}
