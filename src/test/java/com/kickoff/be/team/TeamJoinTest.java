package com.kickoff.be.team;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 팀 가입 신청과 소속 (계약서 §4-3, v1.11.0).
 *
 * 상태 전이는 매칭 신청(§6)과 같은 모양이라 규칙도 같다 — 거절·취소 이력은 재신청을 막지
 * 않고, 대기 중인 신청만 하나로 제한한다.
 *
 * 수락이 <b>명단에 자동 등재</b>까지 한다는 게 이 기능의 핵심이다. 그래서 "수락됐다"만
 * 보지 않고 명단에 실제로 올라왔는지, 그 항목이 계정과 연결됐는지까지 본다.
 */
class TeamJoinTest extends IntegrationTestSupport {

    private User owner;
    private User admin;
    private User applicant;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        admin = createUser("admin@example.com", "최총무", "010-2222-2222");
        applicant = createUser("applicant@example.com", "박멤버", "010-3333-3333");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    // ── 신청

    @Test
    @DisplayName("가입을 신청하면 대기 목록에 나온다")
    void applyAndList() throws Exception {
        apply(applicant, "매주 토요일 나갈 수 있습니다")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.teamId").value(team.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("매주 토요일 나갈 수 있습니다"));

        pending(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].applicant.userId").value(applicant.getId()))
                .andExpect(jsonPath("$[0].applicant.nickname").value("박멤버"));
    }

    @Test
    @DisplayName("메시지 없이도 신청된다")
    void messageIsOptional() throws Exception {
        mockMvc.perform(post("/api/teams/{id}/join", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").isEmpty());
    }

    @Test
    @DisplayName("대기 중인 신청이 있으면 409")
    void duplicateApplyIsConflict() throws Exception {
        apply(applicant, null).andExpect(status().isCreated());

        apply(applicant, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_ALREADY_REQUESTED"));
    }

    @Test
    @DisplayName("이미 소속이면 409 — 소유자·관리자·멤버 전부")
    void alreadyMemberIsConflict() throws Exception {
        apply(owner, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_MEMBER"));

        grantAdmin(admin);
        apply(admin, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_MEMBER"));

        // 멤버가 된 뒤에도 마찬가지
        acceptFirstPending(apply(applicant, null));
        apply(applicant, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_MEMBER"));
    }

    // ── 수락

    @Test
    @DisplayName("수락하면 MEMBER 가 되고 명단에 자동 등재된다")
    void acceptEnrollsIntoRoster() throws Exception {
        acceptFirstPending(apply(applicant, null));

        // 명단에 닉네임으로 올라오고 계정이 연결된다. 포지션·등번호는 비어 있다
        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("박멤버"))
                .andExpect(jsonPath("$[0].userId").value(applicant.getId()))
                .andExpect(jsonPath("$[0].position").isEmpty())
                .andExpect(jsonPath("$[0].backNumber").isEmpty());

        // 역할이 MEMBER 로 보인다
        mockMvc.perform(get("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.isMine").value(false));

        pending(owner).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("관리자도 수락할 수 있다 (팀 페이지 쓰기 권한)")
    void adminCanAccept() throws Exception {
        grantAdmin(admin);
        long joinId = idOf(apply(applicant, null));

        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("명단이 30명이면 수락이 400 이고 신청은 PENDING 으로 남는다")
    void acceptFailsWhenRosterIsFull() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"팀원" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        long joinId = idOf(apply(applicant, null));

        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_MEMBER_LIMIT"));

        // 신청이 살아 있어야 한다 — 자리를 비운 뒤 다시 수락할 수 있어야 하기 때문이다
        pending(owner).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.myJoinStatus").value("PENDING"));
    }

    // ── 거절·취소·재신청

    @Test
    @DisplayName("거절하면 목록에서 빠지고 다시 신청할 수 있다")
    void rejectAllowsReapply() throws Exception {
        long joinId = idOf(apply(applicant, null));

        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/reject",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        pending(owner).andExpect(jsonPath("$", hasSize(0)));
        apply(applicant, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("신청자가 스스로 취소하고 다시 신청할 수 있다")
    void cancelAllowsReapply() throws Exception {
        apply(applicant, null).andExpect(status().isCreated());

        cancel(applicant).andExpect(status().isNoContent());

        pending(owner).andExpect(jsonPath("$", hasSize(0)));
        apply(applicant, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("대기 신청이 없는데 취소하면 404")
    void cancelWithoutPendingIsNotFound() throws Exception {
        cancel(applicant)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOIN_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 처리된 신청을 다시 수락·거절하면 409")
    void reprocessingIsConflict() throws Exception {
        long joinId = idOf(apply(applicant, null));
        acceptById(joinId).andExpect(status().isOk());

        acceptById(joinId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_NOT_PENDING"));
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/reject",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOIN_NOT_PENDING"));
    }

    @Test
    @DisplayName("없는 신청은 404 JOIN_NOT_FOUND")
    void unknownJoinIsNotFound() throws Exception {
        acceptById(99999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOIN_NOT_FOUND"));
    }

    // ── 권한

    @Test
    @DisplayName("무관계한 사람은 대기 목록도 수락도 못 한다 — 403")
    void strangerCannotManageJoins() throws Exception {
        long joinId = idOf(apply(applicant, null));
        User stranger = createUser("stranger@example.com", "남", "010-4444-4444");

        pending(stranger).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MEMBER 는 팀 페이지를 고칠 수 없다 — 조회·소속 표시만")
    void memberHasNoWriteAccess() throws Exception {
        acceptFirstPending(apply(applicant, null));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formation\": \"4-3-3\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"내가추가\"}"))
                .andExpect(status().isForbidden());
        pending(applicant).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인은 신청할 수 없다 — 401")
    void anonymousCannotApply() throws Exception {
        mockMvc.perform(post("/api/teams/{id}/join", team.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ── myJoinStatus (계약서 §4-3, FE 제안 반영)

    @Test
    @DisplayName("myJoinStatus — 대기 중이면 PENDING, 나머지는 전부 null")
    void myJoinStatusPerspectives() throws Exception {
        // 이력 없음
        teamPage(applicant).andExpect(jsonPath("$.myJoinStatus").isEmpty());

        // 대기 중
        long joinId = idOf(apply(applicant, null));
        teamPage(applicant).andExpect(jsonPath("$.myJoinStatus").value("PENDING"));

        // 거절 후 — 재신청이 허용되므로 이력을 구분해 주지 않는다
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/reject",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        teamPage(applicant).andExpect(jsonPath("$.myJoinStatus").isEmpty());

        // 소속이 된 뒤
        acceptFirstPending(apply(applicant, null));
        teamPage(applicant)
                .andExpect(jsonPath("$.myJoinStatus").isEmpty())
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        // 비로그인
        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.myJoinStatus").isEmpty());
    }

    private ResultActions apply(User user, String message) throws Exception {
        var request = post("/api/teams/{id}/join", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON);
        request = request.content(message == null ? "{}" : "{\"message\": \"" + message + "\"}");
        return mockMvc.perform(request);
    }

    private ResultActions cancel(User user) throws Exception {
        return mockMvc.perform(delete("/api/teams/{id}/join", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions pending(User user) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}/join-requests", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions teamPage(User user) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions acceptById(long joinId) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)));
    }

    private void acceptFirstPending(ResultActions applied) throws Exception {
        acceptById(idOf(applied.andExpect(status().isCreated()))).andExpect(status().isOk());
    }

    private void grantAdmin(User target) throws Exception {
        mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + target.getEmail() + "\"}"))
                .andExpect(status().isCreated());
    }
}
