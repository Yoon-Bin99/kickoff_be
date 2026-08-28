package com.kickoff.be.chat;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * 채팅 탭의 방 목록 — GET /api/users/me/chats (계약서 §6-1, v1.13.0).
 *
 * 이 목록에서 조용히 틀리기 쉬운 게 세 가지다.
 * <ul>
 *   <li><b>상대 팀</b> — 내가 글쓴 팀이냐 신청한 팀이냐에 따라 반대쪽이다. 뒤집혀도 에러가
 *       아니라 "내 팀 이름이 상대로 적힌 목록"이 나올 뿐이다</li>
 *   <li><b>메시지 없는 방</b> — 수락 직후의 빈 방은 숨기면 안 된다. 조율을 시작하라는
 *       신호 자체가 사라진다</li>
 *   <li><b>나간 방의 lastMessage</b> — 워터마크를 무시하면 나가기 전 대화가 목록 미리보기로
 *       새어 나온다</li>
 * </ul>
 */
class ChatRoomListTest extends IntegrationTestSupport {

    private User postOwner;
    private User applicantOwner;
    private Team postTeam;
    private Team applicantTeam;
    private MatchRequest accepted;

    @BeforeEach
    void setUpMatch() {
        postOwner = createUser("post@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");

        MatchPost upcoming = createPost(postTeam, "다음 주 토요일 11인제 상대 구합니다",
                OffsetDateTime.now().plusDays(3), false);
        accepted = acceptedRequest(upcoming, applicantTeam);
    }

    @Test
    @DisplayName("수락된 방이 양쪽 목록에 나오고, otherTeam 은 각자의 상대다")
    void bothSidesSeeTheRoomWithTheOpponent() throws Exception {
        myChats(postOwner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].requestId").value(accepted.getId()))
                .andExpect(jsonPath("$[0].postTitle")
                        .value("다음 주 토요일 11인제 상대 구합니다"))
                .andExpect(jsonPath("$[0].matchAt").isNotEmpty())
                .andExpect(jsonPath("$[0].chatOpen").value(true))
                .andExpect(jsonPath("$[0].otherTeam.id").value(applicantTeam.getId()))
                .andExpect(jsonPath("$[0].otherTeam.name").value("마포 유나이티드"))
                // TeamSummary 그대로다 — 평가가 없으면 averageRating 은 null (§2)
                .andExpect(jsonPath("$[0].otherTeam.reviewCount").value(0))
                .andExpect(jsonPath("$[0].otherTeam.averageRating").doesNotExist());

        // 신청한 쪽에서 보면 상대는 글쓴 팀이다
        myChats(applicantOwner)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].otherTeam.id").value(postTeam.getId()));
    }

    @Test
    @DisplayName("메시지가 없으면 lastMessage 는 null 이고, 방은 그대로 목록에 남는다")
    void emptyRoomStaysWithNullLastMessage() throws Exception {
        myChats(postOwner)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastMessage").doesNotExist());
    }

    @Test
    @DisplayName("lastMessage 는 내가 보는 마지막 한 건이다 — SYSTEM 안내도 포함")
    void lastMessageIsTheLatestVisibleOne() throws Exception {
        send(postOwner, accepted, "첫 메시지");
        send(applicantOwner, accepted, "두 번째");

        myChats(postOwner)
                .andExpect(jsonPath("$[0].lastMessage.content").value("두 번째"))
                .andExpect(jsonPath("$[0].lastMessage.type").value("TEXT"))
                .andExpect(jsonPath("$[0].lastMessage.senderTeamId")
                        .value(applicantTeam.getId()));

        leave(applicantOwner, accepted);
        myChats(postOwner)
                .andExpect(jsonPath("$[0].lastMessage.type").value("SYSTEM"))
                .andExpect(jsonPath("$[0].lastMessage.senderTeamId").doesNotExist());
    }

    @Test
    @DisplayName("정렬 — 대화가 있는 방이 최신순으로 먼저, 빈 방은 그 뒤")
    void roomsAreOrderedByLastMessage() throws Exception {
        MatchRequest second = anotherMatch("두 번째 경기");
        MatchRequest third = anotherMatch("세 번째 경기");

        // 두 번째 방에 먼저, 세 번째 방에 나중에 말한다 → 세 번째가 위로 온다.
        // 첫 방(accepted)은 대화가 없으므로 둘 뒤다
        send(postOwner, second, "오래된 대화");
        send(postOwner, third, "최근 대화");

        myChats(postOwner)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].requestId",
                        contains(third.getId().intValue(), second.getId().intValue(),
                                accepted.getId().intValue())));
    }

    @Test
    @DisplayName("나간 방은 목록에서 빠진다 — 상대가 새로 말해도 다시 나오지 않는다")
    void leftRoomNeverComesBack() throws Exception {
        send(postOwner, accepted, "안녕하세요");
        leave(applicantOwner, accepted);

        myChats(applicantOwner).andExpect(jsonPath("$", hasSize(0)));

        send(postOwner, accepted, "계신가요");
        myChats(applicantOwner).andExpect(jsonPath("$", hasSize(0)));

        // 남은 쪽 목록은 그대로다
        myChats(postOwner).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("복귀한 방의 lastMessage 는 워터마크 이후만 본다")
    void restoredRoomHidesOldPreview() throws Exception {
        send(postOwner, accepted, "나가기 전 대화");
        leave(applicantOwner, accepted);
        send(applicantOwner, accepted, "다시 왔습니다");

        // "나가기 전 대화"가 미리보기로 새어 나오면 안 된다
        myChats(applicantOwner)
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lastMessage.content").value("다시 왔습니다"));
    }

    @Test
    @DisplayName("수락 안 된 신청은 방이 아니다 — 팀 없는 사용자는 빈 배열")
    void onlyAcceptedMatchesAreRooms() throws Exception {
        MatchPost another = createPost(postTeam, "수락 안 된 경기",
                OffsetDateTime.now().plusDays(5), false);
        pendingRequest(another, applicantTeam);

        myChats(postOwner).andExpect(jsonPath("$", hasSize(1)));

        User teamless = createUser("teamless@example.com", "팀없음", "010-9999-9999");
        myChats(teamless).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("경기가 지난 방도 목록에 남고 chatOpen 만 false 다")
    void pastMatchStaysReadOnly() throws Exception {
        MatchPost past = createPost(postTeam, "지난 경기",
                OffsetDateTime.now().minusDays(1), false);
        MatchRequest pastAccepted = acceptedRequest(past, applicantTeam);

        myChats(postOwner)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.requestId == " + pastAccepted.getId()
                        + ")].chatOpen", contains(false)));
    }

    @Test
    @DisplayName("비로그인은 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me/chats")).andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private MatchRequest anotherMatch(String title) {
        MatchPost post = createPost(postTeam, title, OffsetDateTime.now().plusDays(5), false);
        return acceptedRequest(post, applicantTeam);
    }

    private ResultActions myChats(User user) throws Exception {
        return mockMvc.perform(get("/api/users/me/chats")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private void send(User user, MatchRequest request, String content) throws Exception {
        mockMvc.perform(post("/api/requests/{id}/chat", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"" + content + "\"}"))
                .andExpect(status().isCreated());
    }

    private void leave(User user, MatchRequest request) throws Exception {
        mockMvc.perform(post("/api/requests/{id}/chat/leave", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent());
    }
}
