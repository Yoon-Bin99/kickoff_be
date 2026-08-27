package com.kickoff.be.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
 * 매칭 채팅 (계약서 §6-1, v1.12.0).
 *
 * 방이 곧 매칭이라 별도 리소스가 없고, 참여자는 <b>두 팀의 주장뿐</b>이다. 그래서 여기서
 * 무겁게 보는 건 두 가지다 — 커서 페이징이 <b>최신부터</b> 잘리는지, 그리고 권한 경계가
 * 정확한지(ADMIN·MEMBER·제3자 전부 막히는지).
 *
 * 시간 경계도 함께 본다. matchAt 이 지나면 전송만 막히고 <b>읽기는 계속 된다</b> —
 * 계좌·장소처럼 주고받은 정보를 나중에 다시 봐야 하기 때문이다.
 */
class ChatTest extends IntegrationTestSupport {

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

    // ── 주고받기

    @Test
    @DisplayName("양 팀 주장이 주고받고, senderTeamId 로 갈린다")
    void bothCaptainsCanTalk() throws Exception {
        send(postOwner, "토요일 7시 맞으시죠?")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(accepted.getId()))
                .andExpect(jsonPath("$.senderTeamId").value(postTeam.getId()))
                .andExpect(jsonPath("$.content").value("토요일 7시 맞으시죠?"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        send(applicantOwner, "네 맞습니다")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.senderTeamId").value(applicantTeam.getId()));

        chat(postOwner, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[*].content",
                        contains("토요일 7시 맞으시죠?", "네 맞습니다")))
                .andExpect(jsonPath("$.chatOpen").value(true));
    }

    @Test
    @DisplayName("상대도 같은 방을 본다 — 방은 매칭 하나에 하나뿐이다")
    void bothSeeTheSameRoom() throws Exception {
        send(postOwner, "안녕하세요").andExpect(status().isCreated());

        chat(applicantOwner, null, null)
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.messages[0].content").value("안녕하세요"));
    }

    // ── 커서 페이징

    @Test
    @DisplayName("after 없으면 최신 limit 개를 오름차순으로 — 첫 로드")
    void firstLoadReturnsLatestAscending() throws Exception {
        for (int i = 1; i <= 5; i++) {
            send(postOwner, "메시지" + i).andExpect(status().isCreated());
        }

        // 최신 3개(3·4·5)가 오래된 것부터 나와야 한다. 오름차순으로 자르면 1·2·3 이 나온다
        chat(postOwner, null, 3)
                .andExpect(jsonPath("$.messages", hasSize(3)))
                .andExpect(jsonPath("$.messages[*].content",
                        contains("메시지3", "메시지4", "메시지5")));
    }

    @Test
    @DisplayName("after 를 주면 그 이후만 — 폴링")
    void afterCursorReturnsOnlyNewer() throws Exception {
        long first = idOf(send(postOwner, "첫 메시지"));
        send(applicantOwner, "두 번째").andExpect(status().isCreated());
        send(postOwner, "세 번째").andExpect(status().isCreated());

        chat(postOwner, first, null)
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[*].content", contains("두 번째", "세 번째")));

        // 새 게 없으면 빈 배열이다 — 폴링이 계속 도는 정상 상태다
        long last = lastMessageId(postOwner);
        chat(postOwner, last, null).andExpect(jsonPath("$.messages", hasSize(0)));
    }

    @Test
    @DisplayName("limit 은 기본 50, 최대 100 으로 잘린다")
    void limitIsClamped() throws Exception {
        send(postOwner, "하나").andExpect(status().isCreated());

        chat(postOwner, null, 0).andExpect(status().isOk());      // 0 이하는 기본값
        chat(postOwner, null, 999).andExpect(status().isOk());    // 넘겨도 400 이 아니다
        chat(postOwner, null, null).andExpect(jsonPath("$.messages", hasSize(1)));
    }

    // ── 권한 (계약서 §6-1: 두 팀 주장만)

    @Test
    @DisplayName("제3자는 읽기도 쓰기도 403")
    void strangerIsForbidden() throws Exception {
        chat(stranger, null, null).andExpect(status().isForbidden());
        send(stranger, "끼어들기").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("팀이 없는 사용자도 403 — 어느 매칭의 당사자도 될 수 없다")
    void userWithoutTeamIsForbidden() throws Exception {
        User teamless = createUser("teamless@example.com", "팀없음", "010-4444-4444");

        chat(teamless, null, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("당사자 팀의 ADMIN·MEMBER 도 못 들어온다 — 주장 1:1 이다")
    void adminAndMemberAreForbidden() throws Exception {
        User admin = createUser("admin@example.com", "최총무", "010-5555-5555");
        mockMvc.perform(post("/api/teams/{id}/admins", postTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + admin.getEmail() + "\"}"))
                .andExpect(status().isCreated());

        User member = createUser("member@example.com", "박멤버", "010-6666-6666");
        long joinId = idOf(mockMvc.perform(post("/api/teams/{id}/join", postTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        postTeam.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isOk());

        chat(admin, null, null).andExpect(status().isForbidden());
        chat(member, null, null).andExpect(status().isForbidden());
        send(admin, "관리자입니다").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("권한을 상태보다 먼저 본다 — 제3자에게 매칭 상태를 알려주지 않는다")
    void permissionIsCheckedBeforeState() throws Exception {
        MatchPost another = createPost(postTeam, "수락 안 된 경기",
                OffsetDateTime.now().plusDays(5), false);
        MatchRequest pending = pendingRequest(another, applicantTeam);

        // 당사자에게는 409(아직 수락 안 됨), 제3자에게는 403 이어야 한다.
        // 순서가 뒤바뀌면 제3자가 409/403 차이로 수락 여부를 알아낼 수 있다.
        mockMvc.perform(get("/api/requests/{id}/chat", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
        mockMvc.perform(get("/api/requests/{id}/chat", pending.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 매칭은 404, 비로그인은 401")
    void notFoundAndUnauthorized() throws Exception {
        mockMvc.perform(get("/api/requests/{id}/chat", 99999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
        mockMvc.perform(get("/api/requests/{id}/chat", accepted.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── 시간 경계

    @Test
    @DisplayName("경기가 지나면 전송은 409, 읽기는 계속 된다")
    void closedRoomIsReadOnly() throws Exception {
        send(postOwner, "경기 전 메시지").andExpect(status().isCreated());

        MatchPost past = createPost(postTeam, "지난 경기",
                OffsetDateTime.now().minusDays(1), false);
        MatchRequest pastAccepted = acceptedRequest(past, applicantTeam);

        mockMvc.perform(post("/api/requests/{id}/chat", pastAccepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"늦은 메시지\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT_CLOSED"));

        // 읽기는 열려 있고 chatOpen 만 false 다 — 주고받은 정보를 계속 볼 수 있어야 한다
        mockMvc.perform(get("/api/requests/{id}/chat", pastAccepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatOpen").value(false));
    }

    // ── 검증

    @Test
    @DisplayName("내용은 1~500자, 공백만은 안 된다")
    void contentValidation() throws Exception {
        send(postOwner, "").andExpect(status().isBadRequest());
        send(postOwner, "   ").andExpect(status().isBadRequest());
        send(postOwner, "가".repeat(501)).andExpect(status().isBadRequest());

        send(postOwner, "가".repeat(500)).andExpect(status().isCreated());
    }

    // ── 푸시

    @Test
    @DisplayName("메시지를 보내면 상대 팀 주장에게만 푸시가 간다")
    void pushGoesToOpponentOnly() throws Exception {
        registerPushToken(postOwner);
        registerPushToken(applicantOwner);

        pushClient.reset();
        send(postOwner, "안녕하세요 잘 부탁드립니다").andExpect(status().isCreated());

        assertThat(pushClient.sent()).hasSize(1);
        var sent = pushClient.last();
        // 보낸 사람에게 자기 메시지 알림이 갈 이유가 없다
        assertThat(sent.to()).isEqualTo("ExponentPushToken[u" + applicantOwner.getId() + "]");
        assertThat(sent.title()).isEqualTo("FC 새벽");
        assertThat(sent.body()).isEqualTo("안녕하세요 잘 부탁드립니다");
        assertThat(sent.data())
                .containsEntry("type", "CHAT_MESSAGE")
                .containsEntry("requestId", accepted.getId())
                .containsKey("postId");
    }

    @Test
    @DisplayName("긴 내용은 앞 50자만 알림에 실린다")
    void longContentIsTruncatedInPush() throws Exception {
        registerPushToken(applicantOwner);
        String long60 = "가".repeat(60);

        pushClient.reset();
        send(postOwner, long60).andExpect(status().isCreated());

        assertThat(pushClient.last().body()).hasSize(50).isEqualTo("가".repeat(50));
        // 저장된 원문은 잘리지 않는다
        chat(applicantOwner, null, null)
                .andExpect(jsonPath("$.messages[0].content").value(long60));
    }

    private void registerPushToken(User user) throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\": \"ExponentPushToken[u" + user.getId() + "]\"}"))
                .andExpect(status().isNoContent());
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

    private long lastMessageId(User user) throws Exception {
        String body = bodyOf(chat(user, null, null));
        var ids = JsonPath.parse(body).read("$.messages[*].id", java.util.List.class);
        return ((Number) ids.get(ids.size() - 1)).longValue();
    }
}
