package com.kickoff.be.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 채팅방 나가기 (계약서 §6-1, v1.13.0).
 *
 * 나가기는 <b>성질이 다른 네 가지</b>를 한 번에 한다 — 내 목록에서 영구히 빠지고, 상대
 * 방에 안내가 남고, 나간 시점 이전이 내게 안 보이게 되고, 전송하면 복귀한다. 각각이
 * 따로 깨질 수 있어서 따로 본다.
 *
 * 가장 조용히 틀리는 건 <b>안내 줄의 위치</b>다. 워터마크를 안내 줄보다 먼저 찍으면
 * 나간 본인이 재입장했을 때 "상대 팀이 채팅방을 나갔습니다"를 보게 된다 — 에러가 아니라
 * 그냥 거짓말하는 화면이라, 이 테스트가 없으면 FE 가 보고할 때까지 아무도 모른다.
 */
class ChatLeaveTest extends IntegrationTestSupport {

    private User postOwner;
    private User applicantOwner;
    private User stranger;
    private Team postTeam;
    private Team applicantTeam;
    private MatchRequest accepted;

    @BeforeEach
    void setUpMatch() {
        postOwner = createUser("post@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        stranger = createUser("stranger@example.com", "남", "010-3333-3333");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");

        MatchPost upcoming = createPost(postTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(3), false);
        accepted = acceptedRequest(upcoming, applicantTeam);
    }

    // ── 기본 동작

    @Test
    @DisplayName("나가면 204, 또 불러도 204 — 멱등이라 안내 줄이 두 번 남지 않는다")
    void leaveIsIdempotent() throws Exception {
        send(postOwner, "안녕하세요").andExpect(status().isCreated());

        leave(applicantOwner).andExpect(status().isNoContent());
        leave(applicantOwner).andExpect(status().isNoContent());

        // 남은 쪽이 보는 방에 안내는 하나뿐이어야 한다. 멱등을 "매번 새로 처리"로
        // 구현하면 상태 코드는 그대로 204 라 API 만 봐서는 멀쩡해 보인다
        chat(postOwner, null, null)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[*].type", contains("TEXT", "SYSTEM")));
    }

    @Test
    @DisplayName("남은 쪽 방에 SYSTEM 안내가 남는다 — senderTeamId 는 null")
    void opponentSeesSystemNotice() throws Exception {
        leave(applicantOwner).andExpect(status().isNoContent());

        chat(postOwner, null, null)
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].type").value("SYSTEM"))
                .andExpect(jsonPath("$.messages[0].senderTeamId").doesNotExist())
                .andExpect(jsonPath("$.messages[0].content")
                        .value("상대 팀이 채팅방을 나갔습니다"));
    }

    @Test
    @DisplayName("보통 메시지는 그대로 TEXT 다 — 기존 계약이 깨지지 않는다")
    void normalMessageIsText() throws Exception {
        send(postOwner, "안녕하세요")
                .andExpect(jsonPath("$.type").value("TEXT"))
                .andExpect(jsonPath("$.senderTeamId").value(postTeam.getId()));
    }

    // ── 나간 쪽이 보는 것

    @Test
    @DisplayName("나간 쪽은 지난 대화가 안 보인다 — 자기가 나갔다는 안내도 안 보인다")
    void leaverSeesNothingBeforeLeaving() throws Exception {
        send(postOwner, "토요일 7시 맞으시죠?").andExpect(status().isCreated());
        send(applicantOwner, "네 맞습니다").andExpect(status().isCreated());

        leave(applicantOwner).andExpect(status().isNoContent());

        // 안내 줄까지 워터마크에 포함돼야 한다. 순서를 뒤집으면 여기에 SYSTEM 한 줄이
        // 남아서, 나간 본인이 "상대 팀이 나갔다"는 안내를 보게 된다
        chat(applicantOwner, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    @DisplayName("나간 뒤 상대가 보낸 것만 보인다 — 재입장은 막지 않는다")
    void leaverSeesOnlyLaterMessages() throws Exception {
        send(postOwner, "예전 이야기").andExpect(status().isCreated());
        leave(applicantOwner).andExpect(status().isNoContent());
        send(postOwner, "그래도 한 마디 남깁니다").andExpect(status().isCreated());

        chat(applicantOwner, null, null)
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("그래도 한 마디 남깁니다"));

        // 남은 쪽은 전부 그대로 본다 — 나가기는 나간 쪽에게만 작용한다
        chat(postOwner, null, null).andExpect(jsonPath("$.messages", hasSize(3)));
    }

    @Test
    @DisplayName("after 를 과거로 줘도 워터마크를 넘어서 보이지 않는다 — 폴링으로 우회 불가")
    void watermarkWinsOverAfterCursor() throws Exception {
        send(postOwner, "예전 이야기").andExpect(status().isCreated());
        leave(applicantOwner).andExpect(status().isNoContent());
        send(postOwner, "새 이야기").andExpect(status().isCreated());

        // FE 가 나가기 전에 받아 둔 커서를 그대로 들고 폴링해도 지난 대화는 오지 않는다
        chat(applicantOwner, 0L, null)
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("새 이야기"));
    }

    // ── 복귀

    @Test
    @DisplayName("전송하면 복귀 — 목록에 다시 나오고 푸시도 재개된다")
    void sendingRestoresTheRoom() throws Exception {
        registerPushToken(applicantOwner);
        leave(applicantOwner).andExpect(status().isNoContent());
        myChats(applicantOwner).andExpect(jsonPath("$", hasSize(0)));

        send(applicantOwner, "다시 왔습니다").andExpect(status().isCreated());

        myChats(applicantOwner)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastMessage.content").value("다시 왔습니다"));

        pushClient.reset();
        send(postOwner, "반갑습니다").andExpect(status().isCreated());
        assertThat(pushClient.sent()).hasSize(1);
    }

    @Test
    @DisplayName("복귀해도 지난 대화는 돌아오지 않는다 — 워터마크는 영구다")
    void rejoiningDoesNotRestoreHistory() throws Exception {
        send(postOwner, "지워질 이야기").andExpect(status().isCreated());
        leave(applicantOwner).andExpect(status().isNoContent());
        send(applicantOwner, "다시 왔습니다").andExpect(status().isCreated());

        chat(applicantOwner, null, null)
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("다시 왔습니다"));
    }

    // ── 푸시

    @Test
    @DisplayName("나간 쪽에게는 CHAT_MESSAGE 푸시가 가지 않는다")
    void noPushToTheTeamThatLeft() throws Exception {
        registerPushToken(applicantOwner);
        leave(applicantOwner).andExpect(status().isNoContent());

        pushClient.reset();
        send(postOwner, "듣고 계신가요").andExpect(status().isCreated());

        // 나가기의 뜻이 "이 방에서 손 뗀다"인데 알림만 계속 오면 아무 일도 안 한 셈이 된다
        assertThat(pushClient.sent()).isEmpty();
    }

    @Test
    @DisplayName("SYSTEM 안내는 푸시를 보내지 않는다")
    void systemNoticeSendsNoPush() throws Exception {
        registerPushToken(postOwner);
        registerPushToken(applicantOwner);

        pushClient.reset();
        leave(applicantOwner).andExpect(status().isNoContent());

        assertThat(pushClient.sent()).isEmpty();
    }

    // ── 권한·경계 (GET /chat 과 같은 규칙)

    @Test
    @DisplayName("제3자·ADMIN 은 403, 수락 전은 409, 없는 매칭은 404, 비로그인은 401")
    void permissionAndStateRules() throws Exception {
        leave(stranger).andExpect(status().isForbidden());

        MatchPost another = createPost(postTeam, "수락 안 된 경기",
                OffsetDateTime.now().plusDays(5), false);
        MatchRequest pending = pendingRequest(another, applicantTeam);
        mockMvc.perform(post("/api/requests/{id}/chat/leave", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
        // 제3자에게는 수락 여부를 알려주지 않는다 — 권한을 먼저 본다
        mockMvc.perform(post("/api/requests/{id}/chat/leave", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/requests/{id}/chat/leave", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mockMvc.perform(post("/api/requests/{id}/chat/leave", accepted.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("나가기는 채팅에만 닿는다 — 매칭 자체는 그대로 수락 상태다")
    void leavingDoesNotTouchTheMatch() throws Exception {
        leave(applicantOwner).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(accepted.getId()))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    // ── 헬퍼

    private void registerPushToken(User user) throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\": \"ExponentPushToken[u" + user.getId() + "]\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions leave(User user) throws Exception {
        return mockMvc.perform(post("/api/requests/{id}/chat/leave", accepted.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions send(User user, String content) throws Exception {
        return mockMvc.perform(post("/api/requests/{id}/chat", accepted.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + content + "\"}"));
    }

    private ResultActions chat(User user, Long after, Integer limit) throws Exception {
        var request = get("/api/requests/{id}/chat", accepted.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user));
        if (after != null) {
            request = request.param("after", String.valueOf(after));
        }
        if (limit != null) {
            request = request.param("limit", String.valueOf(limit));
        }
        return mockMvc.perform(request);
    }

    private ResultActions myChats(User user) throws Exception {
        return mockMvc.perform(get("/api/users/me/chats")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
