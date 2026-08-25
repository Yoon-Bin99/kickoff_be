package com.kickoff.be.team;

import static org.hamcrest.Matchers.hasSize;
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
 * 내가 소유·관리하는 팀 목록 (계약서 §4-2, v1.9.1).
 *
 * 이 엔드포인트가 생긴 이유는 관리자로 임명된 사람이 <b>그 팀을 되찾아갈 길이 없어서</b>다.
 * 소유 팀은 {@code GET /api/teams/me} 로 찾지만 관리 팀은 어디에도 안 걸렸다.
 */
class MyTeamsTest extends IntegrationTestSupport {

    private User owner;

    @BeforeEach
    void setUpOwner() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("소유한 팀만 있으면 OWNER 하나")
    void onlyOwned() throws Exception {
        Team team = createTeam(owner, "FC 새벽", "서울 강서구");

        myTeams(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].team.id").value(team.getId()))
                .andExpect(jsonPath("$[0].team.name").value("FC 새벽"));
    }

    @Test
    @DisplayName("관리만 하면 ADMIN 하나 — 자기 팀이 없어도 된다")
    void onlyAdministered() throws Exception {
        Team team = createTeam(owner, "FC 새벽", "서울 강서구");
        User admin = createUser("admin@example.com", "최총무", "010-2222-2222");
        grant(team, admin);

        myTeams(admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].team.id").value(team.getId()));
    }

    @Test
    @DisplayName("둘 다면 소유가 먼저, 관리는 임명순")
    void ownedComesFirst() throws Exception {
        Team mine = createTeam(owner, "FC 새벽", "서울 강서구");

        User otherOwner = createUser("other@example.com", "다른주장", "010-3333-3333");
        Team first = createTeam(otherOwner, "먼저 임명한 팀", "서울 마포구");
        User thirdOwner = createUser("third@example.com", "또다른주장", "010-4444-4444");
        Team second = createTeam(thirdOwner, "나중 임명한 팀", "서울 송파구");

        grant(first, owner);
        grant(second, owner);

        myTeams(owner)
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].role").value("OWNER"))
                .andExpect(jsonPath("$[0].team.id").value(mine.getId()))
                .andExpect(jsonPath("$[1].role").value("ADMIN"))
                .andExpect(jsonPath("$[1].team.id").value(first.getId()))
                .andExpect(jsonPath("$[2].role").value("ADMIN"))
                .andExpect(jsonPath("$[2].team.id").value(second.getId()));
    }

    @Test
    @DisplayName("아무것도 없으면 빈 배열 — 404 가 아니다")
    void noneIsEmptyArray() throws Exception {
        myTeams(owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("관리자에서 해제되면 목록에서도 빠진다")
    void revokedTeamDisappears() throws Exception {
        Team team = createTeam(owner, "FC 새벽", "서울 강서구");
        User admin = createUser("admin@example.com", "최총무", "010-2222-2222");
        grant(team, admin);

        myTeams(admin).andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/teams/{id}/admins/{userId}", team.getId(), admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        myTeams(admin).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("비로그인은 볼 수 없다 — 401")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")).andExpect(status().isUnauthorized());
    }

    private void grant(Team team, User target) throws Exception {
        mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(team.getOwner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + target.getEmail() + "\"}"))
                .andExpect(status().isCreated());
    }

    private ResultActions myTeams(User user) throws Exception {
        return mockMvc.perform(get("/api/users/me/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
