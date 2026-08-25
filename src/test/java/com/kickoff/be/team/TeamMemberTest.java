package com.kickoff.be.team;

import static org.hamcrest.Matchers.contains;
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
 * 팀원 명단 (계약서 §4-1, v1.8.0).
 *
 * 정렬 규칙이 이 기능에서 제일 미묘하다 — "등번호 오름차순, 등번호 없으면 뒤에 이름순"인데
 * null 을 앞에 둘지 뒤에 둘지는 DB 마다 기본값이 다르다. H2 는 앞, PostgreSQL 은 뒤다.
 * 명시하지 않으면 개발과 운영에서 명단 순서가 갈리므로 쿼리에서 못 박았고, 그 규칙을
 * 여기서 고정한다.
 */
class TeamMemberTest extends IntegrationTestSupport {

    private User owner;
    private User stranger;
    private Team team;

    @BeforeEach
    void setUpTeam() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        stranger = createUser("stranger@example.com", "남", "010-2222-2222");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("팀원을 추가하면 목록에 나온다 — 조회는 비로그인도 된다")
    void addAndList() throws Exception {
        addMember("{\"name\": \"김철수\", \"position\": \"MF\", \"backNumber\": 8}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.position").value("MF"))
                .andExpect(jsonPath("$.backNumber").value(8));

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("김철수"));
    }

    @Test
    @DisplayName("포지션·등번호 없이도 추가된다")
    void positionAndNumberAreOptional() throws Exception {
        addMember("{\"name\": \"이영희\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").isEmpty())
                .andExpect(jsonPath("$.backNumber").isEmpty());
    }

    @Test
    @DisplayName("등번호 오름차순, 없는 사람은 뒤에 이름순")
    void orderingRule() throws Exception {
        addMember("{\"name\": \"박지성\", \"backNumber\": 7}").andExpect(status().isCreated());
        addMember("{\"name\": \"하차순\"}").andExpect(status().isCreated());
        addMember("{\"name\": \"손흥민\", \"backNumber\": 0}").andExpect(status().isCreated());
        addMember("{\"name\": \"가나다\"}").andExpect(status().isCreated());
        addMember("{\"name\": \"이강인\", \"backNumber\": 18}").andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$[*].name",
                        contains("손흥민", "박지성", "이강인", "가나다", "하차순")));
    }

    @Test
    @DisplayName("팀원은 30명까지 — 서른한 번째는 400")
    void memberLimitIsThirty() throws Exception {
        for (int i = 0; i < 30; i++) {
            addMember("{\"name\": \"팀원" + i + "\"}").andExpect(status().isCreated());
        }

        addMember("{\"name\": \"서른한번째\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_MEMBER_LIMIT"));

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$", hasSize(30)));
    }

    @Test
    @DisplayName("PATCH 로 고치고, 포지션·등번호는 null 로 지운다")
    void patchAndClear() throws Exception {
        long memberId = idOf(addMember(
                "{\"name\": \"김철수\", \"position\": \"MF\", \"backNumber\": 8}"));

        patchMember(owner, memberId, "{\"name\": \"김철수2\", \"position\": \"FW\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수2"))
                .andExpect(jsonPath("$.position").value("FW"))
                .andExpect(jsonPath("$.backNumber").value(8));

        patchMember(owner, memberId, "{\"position\": null, \"backNumber\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").isEmpty())
                .andExpect(jsonPath("$.backNumber").isEmpty())
                .andExpect(jsonPath("$.name").value("김철수2"));
    }

    @Test
    @DisplayName("이름은 null 로 지울 수 없다 — 400")
    void nameCannotBeCleared() throws Exception {
        long memberId = idOf(addMember("{\"name\": \"김철수\"}"));

        patchMember(owner, memberId, "{\"name\": null}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @DisplayName("등번호는 0~99, 이름은 1~20자")
    void validationRules() throws Exception {
        addMember("{\"name\": \"김철수\", \"backNumber\": 100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("backNumber"));
        addMember("{\"name\": \"김철수\", \"backNumber\": -1}")
                .andExpect(status().isBadRequest());
        addMember("{\"name\": \"\"}").andExpect(status().isBadRequest());
        addMember("{\"name\": \"" + "가".repeat(21) + "\"}").andExpect(status().isBadRequest());

        // 경계값은 통과한다
        addMember("{\"name\": \"김철수\", \"backNumber\": 0}").andExpect(status().isCreated());
        addMember("{\"name\": \"이영희\", \"backNumber\": 99}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("삭제하면 목록에서 빠진다")
    void deleteMember() throws Exception {
        long memberId = idOf(addMember("{\"name\": \"김철수\"}"));

        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/teams/{id}/members", team.getId()))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("없는 팀원은 404 MEMBER_NOT_FOUND")
    void memberNotFound() throws Exception {
        patchMember(owner, 99999L, "{\"name\": \"없는사람\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", team.getId(), 99999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("다른 팀 팀원을 내 팀 경로로 고칠 수 없다 — 404")
    void cannotReachAnotherTeamsMember() throws Exception {
        User otherOwner = createUser("other@example.com", "다른주장", "010-3333-3333");
        Team otherTeam = createTeam(otherOwner, "FC 노을", "서울 마포구");
        long otherMemberId = idOf(mockMvc.perform(post("/api/teams/{id}/members", otherTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"남의팀원\"}"))
                .andExpect(status().isCreated()));

        // 내 팀 경로로 권한은 통과하지만 대상이 남의 팀 것이라 404 여야 한다
        patchMember(owner, otherMemberId, "{\"name\": \"가로채기\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    @DisplayName("무관계한 사람은 명단을 고칠 수 없다 — 403")
    void strangerCannotWrite() throws Exception {
        long memberId = idOf(addMember("{\"name\": \"김철수\"}"));

        mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"침입자\"}"))
                .andExpect(status().isForbidden());
        patchMember(stranger, memberId, "{\"name\": \"침입자\"}").andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자도 명단을 고칠 수 있다 (계약서 §4-2 권한표)")
    void adminCanWriteMembers() throws Exception {
        User admin = createUser("admin@example.com", "최총무", "010-4444-4444");
        mockMvc.perform(post("/api/teams/{id}/admins", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + admin.getEmail() + "\"}"))
                .andExpect(status().isCreated());

        long memberId = idOf(mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"관리자가 추가\"}"))
                .andExpect(status().isCreated()));

        patchMember(admin, memberId, "{\"backNumber\": 10}").andExpect(status().isOk());
        mockMvc.perform(delete("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
    }

    private ResultActions addMember(String body) throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/members", team.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchMember(User actor, long memberId, String body) throws Exception {
        return mockMvc.perform(patch("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
