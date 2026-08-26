package com.kickoff.be.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.push.dto.TeamJoinPushType;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 명단-계정 통합, 탈퇴·강퇴, 팀 검색, 가입 푸시 (계약서 §4·§4-3, v1.11.0).
 *
 * 소속이 <b>명단 항목으로 표현된다</b>는 게 이 묶음의 전제다. 그래서 항목을 지우는 것이
 * 곧 강퇴이고, 탈퇴도 항목을 지운다. 두 개념을 따로 두면 "명단에서만 지웠는데 아직 멤버"인
 * 상태가 생긴다.
 */
class TeamMembershipTest extends IntegrationTestSupport {

    private User owner;
    private User member;
    private Team team;

    @BeforeEach
    void setUpMembership() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        member = createUser("member@example.com", "박멤버", "010-2222-2222");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    // ── 탈퇴·강퇴

    @Test
    @DisplayName("탈퇴하면 명단에서도 빠지고 역할이 사라진다")
    void leaveRemovesRoster() throws Exception {
        joinAndAccept(member);

        mockMvc.perform(delete("/api/teams/{id}/membership", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(jsonPath("$.myRole").isEmpty());
    }

    @Test
    @DisplayName("계정 연결 항목을 지우면 강퇴 — 멤버십도 함께 끝난다")
    void deletingLinkedMemberIsKick() throws Exception {
        joinAndAccept(member);
        long memberId = firstMemberId();

        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(jsonPath("$.myRole").isEmpty());
        // 강퇴된 뒤에는 다시 신청할 수 있다
        apply(member).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("소유자·비소속은 탈퇴 API 로 나갈 수 없다 — 403")
    void ownerAndStrangerCannotLeave() throws Exception {
        mockMvc.perform(delete("/api/teams/{id}/membership", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/teams/{id}/membership", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(member)))
                .andExpect(status().isForbidden());
    }

    // ── 이름은 닉네임을 따른다

    @Test
    @DisplayName("계정 연결 항목의 이름은 PATCH 로 못 바꾼다 — 400")
    void linkedMemberNameIsLocked() throws Exception {
        joinAndAccept(member);
        long memberId = firstMemberId();

        patchMember(memberId, "{\"name\": \"주장이 바꾼 이름\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));

        // 포지션·등번호는 주장이 채우는 값이라 그대로 고칠 수 있다
        patchMember(memberId, "{\"position\": \"MF\", \"backNumber\": 10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value("MF"))
                .andExpect(jsonPath("$.name").value("박멤버"));
    }

    @Test
    @DisplayName("수기 명단의 이름은 그대로 바꿀 수 있다 — 잠기는 건 연결된 항목뿐")
    void manualMemberNameIsEditable() throws Exception {
        long manualId = idOf(mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"수기팀원\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isEmpty()));

        patchMember(manualId, "{\"name\": \"이름변경\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("이름변경"));
    }

    @Test
    @DisplayName("닉네임을 바꾸면 명단 이름도 따라간다")
    void nicknameChangePropagatesToRoster() throws Exception {
        joinAndAccept(member);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\": \"박멤버2\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$[0].name").value("박멤버2"))
                .andExpect(jsonPath("$[0].userId").value(member.getId()));
    }

    // ── /users/me/teams 순서

    @Test
    @DisplayName("내 팀 목록은 소유 → 관리 → 소속 순이다")
    void myTeamsOrdering() throws Exception {
        User other = createUser("other@example.com", "다른주장", "010-3333-3333");
        Team adminTeam = createTeam(other, "관리하는 팀", "서울 마포구");
        Team memberTeam = createTeam(
                createUser("third@example.com", "또다른주장", "010-4444-4444"),
                "소속된 팀", "서울 송파구");

        // owner 를 adminTeam 의 관리자로, memberTeam 의 멤버로 만든다
        mockMvc.perform(post("/api/teams/{id}/admins", adminTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.getEmail() + "\"}"))
                .andExpect(status().isCreated());
        joinAndAccept(owner, memberTeam, memberTeam.getOwner());

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].role", contains("OWNER", "ADMIN", "MEMBER")))
                .andExpect(jsonPath("$[0].team.name").value("FC 새벽"))
                .andExpect(jsonPath("$[1].team.name").value("관리하는 팀"))
                .andExpect(jsonPath("$[2].team.name").value("소속된 팀"));
    }

    // ── 팀 검색

    @Test
    @DisplayName("팀 검색 — 이름·지역 부분 일치, 생성일 DESC")
    void teamSearch() throws Exception {
        createTeam(createUser("a@example.com", "가", "010-5555-0001"), "마포 유나이티드", "서울 마포구");
        createTeam(createUser("b@example.com", "나", "010-5555-0002"), "송파 FC", "서울 송파구");

        // 필터 없으면 전체, 최근 생성 순
        mockMvc.perform(get("/api/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("송파 FC"))
                .andExpect(jsonPath("$.content[2].name").value("FC 새벽"));

        mockMvc.perform(get("/api/teams").param("keyword", "마포"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("마포 유나이티드"));
        mockMvc.perform(get("/api/teams").param("region", "서울"))
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/teams").param("region", "송파"))
                .andExpect(jsonPath("$.content", hasSize(1)));
        // 빈 문자열은 필터를 안 건 것과 같다
        mockMvc.perform(get("/api/teams").param("keyword", "  "))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    @DisplayName("팀 검색은 비로그인도 되고 평점이 실린다")
    void teamSearchIsPublicAndCarriesRating() throws Exception {
        mockMvc.perform(get("/api/teams").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].averageRating").isEmpty())
                .andExpect(jsonPath("$.content[0].reviewCount").value(0));
    }

    @Test
    @DisplayName("팀 검색 size 는 최대 50 으로 잘린다")
    void teamSearchSizeIsClamped() throws Exception {
        mockMvc.perform(get("/api/teams").param("size", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
    }

    // ── 푸시 3종

    @Test
    @DisplayName("가입 신청·수락·거절에 푸시가 나간다")
    void joinPushEvents() throws Exception {
        registerPushToken(owner);
        registerPushToken(member);

        // 테스트 프로파일은 동기 실행기라 발송이 요청 안에서 끝난다 (StubPushConfig)
        pushClient.reset();
        long joinId = idOf(apply(member).andExpect(status().isCreated()));
        assertThat(pushClient.last().title())
                .isEqualTo(TeamJoinPushType.JOIN_REQUEST_RECEIVED.title());
        assertThat(pushClient.last().data()).containsEntry("type", "JOIN_REQUEST_RECEIVED");

        pushClient.reset();
        acceptById(joinId).andExpect(status().isOk());
        assertThat(pushClient.last().title())
                .isEqualTo(TeamJoinPushType.JOIN_ACCEPTED.title());

        // 강퇴 후 다시 신청 → 거절
        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}",
                        team.getId(), firstMemberId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        long secondJoinId = idOf(apply(member).andExpect(status().isCreated()));

        pushClient.reset();
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/reject",
                        team.getId(), secondJoinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        var rejected = pushClient.last();
        assertThat(rejected.title()).isEqualTo(TeamJoinPushType.JOIN_REJECTED.title());
        assertThat(rejected.data()).containsEntry("teamId", team.getId());
    }

    private void registerPushToken(User user) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\": \"ExponentPushToken[" + user.getId() + "]\"}"))
                .andExpect(status().isNoContent());
    }

    private ResultActions apply(User user) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/join", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    private ResultActions acceptById(long joinId) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private void joinAndAccept(User user) throws Exception {
        joinAndAccept(user, team, owner);
    }

    private void joinAndAccept(User user, Team target, User targetOwner) throws Exception {
        long joinId = idOf(mockMvc.perform(post("/api/teams/{id}/join", target.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()));
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        target.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(targetOwner)))
                .andExpect(status().isOk());
    }

    private long firstMemberId() throws Exception {
        return com.jayway.jsonpath.JsonPath.parse(bodyOf(
                mockMvc.perform(get("/api/teams/{id}/members", team.getId()))))
                .read("$[0].id", Integer.class).longValue();
    }

    private ResultActions patchMember(long memberId, String body) throws Exception {
        return mockMvc.perform(patch("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
