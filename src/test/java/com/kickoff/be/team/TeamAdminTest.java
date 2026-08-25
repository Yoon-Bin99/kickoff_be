package com.kickoff.be.team;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * 팀 관리자 (계약서 §4-2, v1.9.0).
 *
 * 여기서 제일 중요한 건 <b>권한 경계</b>다. 관리자는 팀 페이지를 고칠 수 있지만 모집글·
 * 신청·리뷰·관리자 임명은 못 한다. 경계가 한 칸이라도 새면 남의 팀 이름으로 글이 나가거나
 * 관리자가 관리자를 늘려 소유자가 통제를 잃는다. 그래서 "되는 것"과 "안 되는 것"을 같은
 * 무게로 고정한다.
 */
class TeamAdminTest extends IntegrationTestSupport {

    private User owner;
    private User admin;
    private User stranger;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        admin = createUser("admin@example.com", "최총무", "010-2222-2222");
        stranger = createUser("stranger@example.com", "남", "010-3333-3333");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    // ── 임명·해제·목록

    @Test
    @DisplayName("소유자가 이메일로 관리자를 임명한다")
    void ownerGrantsAdmin() throws Exception {
        grant(owner, admin.getEmail())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(admin.getId()))
                .andExpect(jsonPath("$.nickname").value("최총무"))
                .andExpect(jsonPath("$.grantedAt").isNotEmpty());

        admins(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(admin.getId()));
    }

    @Test
    @DisplayName("관리자도 관리자 목록은 볼 수 있다 — 같은 권한을 가진 사람은 알아야 한다")
    void adminCanListAdmins() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        admins(admin).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        // 무관계는 목록도 못 본다
        admins(stranger).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("해제하면 목록에서 사라지고 권한도 잃는다")
    void revokeRemovesAdmin() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/teams/{id}/admins/{userId}", team.getId(), admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        admins(owner).andExpect(jsonPath("$", hasSize(0)));
        patchTeam(admin, "{\"formation\": \"4-3-3\"}").andExpect(status().isForbidden());
    }

    // ── 에러 3종

    @Test
    @DisplayName("이미 관리자면 409 — 소유자 본인을 임명해도 409")
    void duplicateGrantIsConflict() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        grant(owner, admin.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_ADMIN"));

        // 소유자는 이미 더 넓은 권한을 가졌다. 허용하면 "소유자이면서 관리자"가 된다
        grant(owner, owner.getEmail())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_TEAM_ADMIN"));
    }

    @Test
    @DisplayName("관리자는 5명까지 — 여섯 번째는 400")
    void adminLimitIsFive() throws Exception {
        for (int i = 0; i < 5; i++) {
            User candidate = createUser("admin" + i + "@example.com", "관리자" + i,
                    "010-9999-000" + i);
            grant(owner, candidate.getEmail()).andExpect(status().isCreated());
        }

        User sixth = createUser("sixth@example.com", "여섯번째", "010-9999-0009");
        grant(owner, sixth.getEmail())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_ADMIN_LIMIT"));

        admins(owner).andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    @DisplayName("없는 사용자는 404, 관리자가 아닌 사람을 해제하면 404")
    void notFoundCases() throws Exception {
        grant(owner, "nobody@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        mockMvc.perform(delete("/api/teams/{id}/admins/{userId}", team.getId(), stranger.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_NOT_FOUND"));
    }

    // ── 권한표 (계약서 §4-2). 되는 것과 안 되는 것을 같은 무게로 본다.

    @Test
    @DisplayName("관리자는 팀 정보를 고칠 수 있다")
    void adminCanPatchTeam() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        patchTeam(admin, "{\"formation\": \"4-3-3\", \"teamColor\": \"#123456\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formation").value("4-3-3"))
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                // 소유자가 아니므로 isMine 은 false 다 (하위호환 유지)
                .andExpect(jsonPath("$.isMine").value(false));
    }

    @Test
    @DisplayName("관리자는 관리자를 임명·해제할 수 없다 — 403")
    void adminCannotManageAdmins() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        grant(admin, stranger.getEmail()).andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/teams/{id}/admins/{userId}", team.getId(), admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 그 팀 이름으로 모집글을 쓸 수 없다 — 팀 페이지 권한일 뿐이다")
    void adminCannotWritePosts() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        // admin 은 자기 팀이 없다. 글 작성은 "자기 팀" 기준이라 팀 관리 권한과 축이 다르다
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "관리자가 쓰는 글", "content": "권한 경계 확인",
                                 "matchAt": "2027-01-01T10:00:00+09:00",
                                 "location": "강서구민운동장", "region": "서울 강서구",
                                 "fieldType": "SOCCER_11"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_REQUIRED"));
    }

    @Test
    @DisplayName("무관계한 사람은 팀 정보를 못 고친다 — 403")
    void strangerCannotPatchTeam() throws Exception {
        patchTeam(stranger, "{\"formation\": \"4-3-3\"}").andExpect(status().isForbidden());
    }

    // ── myRole

    @Test
    @DisplayName("myRole 은 소유자 OWNER, 관리자 ADMIN, 나머지 null")
    void myRoleReflectsRelationship() throws Exception {
        grant(owner, admin.getEmail()).andExpect(status().isCreated());

        getTeam(owner)
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.isMine").value(true));
        getTeam(admin)
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.isMine").value(false));
        getTeam(stranger).andExpect(jsonPath("$.myRole").isEmpty());

        // 비로그인
        mockMvc.perform(get("/api/teams/{id}", team.getId()))
                .andExpect(jsonPath("$.myRole").isEmpty())
                .andExpect(jsonPath("$.isMine").value(false));
    }

    @Test
    @DisplayName("한 사람이 여러 팀의 관리자가 될 수 있다")
    void oneUserCanAdministerManyTeams() throws Exception {
        User otherOwner = createUser("other@example.com", "다른주장", "010-4444-4444");
        Team otherTeam = createTeam(otherOwner, "FC 노을", "서울 마포구");

        grant(owner, admin.getEmail()).andExpect(status().isCreated());
        mockMvc.perform(post("/api/teams/{id}/admins", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + admin.getEmail() + "\"}"))
                .andExpect(status().isCreated());

        patchTeam(admin, "{\"formation\": \"4-4-2\"}").andExpect(status().isOk());
        mockMvc.perform(patch("/api/teams/{id}", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formation\": \"3-4-3\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions grant(User actor, String email) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions admins(User actor) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}/admins", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    private ResultActions getTeam(User actor) throws Exception {
        return mockMvc.perform(get("/api/teams/{id}", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor)));
    }

    private ResultActions patchTeam(User actor, String body) throws Exception {
        return mockMvc.perform(patch("/api/teams/{id}", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
