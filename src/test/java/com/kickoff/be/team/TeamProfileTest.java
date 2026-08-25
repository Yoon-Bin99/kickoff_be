package com.kickoff.be.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.Year;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 팀 프로필 확장 — 창단 연도·팀 색·포메이션 (계약서 §4-1, v1.8.0).
 *
 * 셋 다 nullable 이고 v1.5.1 지우기 규칙을 탄다. 창단 연도의 상한만 특이한데, "올해"라서
 * 어노테이션 상수로 못 박고 서비스가 본다 — 그래서 여기서도 상수 대신
 * {@link Year#now()} 로 기대값을 만든다. 2027년이 되면 깨지는 테스트를 남기지 않으려는
 * 것이다.
 */
class TeamProfileTest extends IntegrationTestSupport {

    private User owner;

    @BeforeEach
    void setUpOwner() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("팀을 만들 때 프로필 확장 필드를 함께 넣는다")
    void createStoresProfileFields() throws Exception {
        long teamId = idOf(createTeam("\"foundedYear\": 2020, \"teamColor\": \"#1B7F4B\","
                + " \"formation\": \"4-4-2\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foundedYear").value(2020))
                .andExpect(jsonPath("$.teamColor").value("#1B7F4B"))
                .andExpect(jsonPath("$.formation").value("4-4-2")));

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(jsonPath("$.foundedYear").value(2020))
                .andExpect(jsonPath("$.teamColor").value("#1B7F4B"))
                .andExpect(jsonPath("$.formation").value("4-4-2"));
    }

    @Test
    @DisplayName("안 넣으면 전부 null — 몰라도 팀은 만들어진다")
    void profileFieldsAreOptional() throws Exception {
        createTeam(null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foundedYear").isEmpty())
                .andExpect(jsonPath("$.teamColor").isEmpty())
                .andExpect(jsonPath("$.formation").isEmpty());
    }

    @Test
    @DisplayName("PATCH 로 채우고 바꿀 수 있다")
    void profileFieldsCanBePatched() throws Exception {
        long teamId = idOf(createTeam(null));

        patchTeam(teamId, "{\"foundedYear\": 2015, \"teamColor\": \"#FF0000\","
                + " \"formation\": \"3-5-2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foundedYear").value(2015))
                .andExpect(jsonPath("$.teamColor").value("#FF0000"))
                .andExpect(jsonPath("$.formation").value("3-5-2"));
    }

    @Test
    @DisplayName("명시적 null 로 지워진다 (v1.5.1 규칙)")
    void profileFieldsCanBeCleared() throws Exception {
        long teamId = idOf(createTeam("\"foundedYear\": 2020, \"teamColor\": \"#1B7F4B\","
                + " \"formation\": \"4-4-2\""));

        patchTeam(teamId, "{\"foundedYear\": null, \"teamColor\": null, \"formation\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foundedYear").isEmpty())
                .andExpect(jsonPath("$.teamColor").isEmpty())
                .andExpect(jsonPath("$.formation").isEmpty());

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(jsonPath("$.teamColor").isEmpty());
    }

    @Test
    @DisplayName("안 보낸 프로필 필드는 그대로 둔다")
    void absentProfileFieldsAreUntouched() throws Exception {
        long teamId = idOf(createTeam("\"foundedYear\": 2020, \"teamColor\": \"#1B7F4B\","
                + " \"formation\": \"4-4-2\""));

        patchTeam(teamId, "{\"teamColor\": \"#0000FF\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamColor").value("#0000FF"))
                .andExpect(jsonPath("$.foundedYear").value(2020))
                .andExpect(jsonPath("$.formation").value("4-4-2"));
    }

    @Test
    @DisplayName("팀 색은 #RRGGBB 형식만 받는다")
    void teamColorFormatIsChecked() throws Exception {
        long teamId = idOf(createTeam(null));

        for (String bad : new String[] {"1B7F4B", "#1B7F4", "#GGGGGG", "red", "#1b7f4b7"}) {
            patchTeam(teamId, "{\"teamColor\": \"" + bad + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("teamColor"));
        }

        // 소문자 16진수는 통과한다 — 같은 색을 두 표기로 쓸 뿐이다
        patchTeam(teamId, "{\"teamColor\": \"#1b7f4b\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("창단 연도는 1900년부터 올해까지 — 상한은 서버 시간 기준이다")
    void foundedYearRangeIsChecked() throws Exception {
        int thisYear = Year.now().getValue();
        long teamId = idOf(createTeam(null));

        patchTeam(teamId, "{\"foundedYear\": 1899}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("foundedYear"));

        patchTeam(teamId, "{\"foundedYear\": " + (thisYear + 1) + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("foundedYear"));

        // 경계값 둘 다 통과한다
        patchTeam(teamId, "{\"foundedYear\": 1900}").andExpect(status().isOk());
        patchTeam(teamId, "{\"foundedYear\": " + thisYear + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foundedYear").value(thisYear));
    }

    @Test
    @DisplayName("미래 연도는 팀 생성에서도 막힌다")
    void futureFoundedYearIsRejectedOnCreate() throws Exception {
        createTeam("\"foundedYear\": " + (Year.now().getValue() + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("foundedYear"));
    }

    @Test
    @DisplayName("남의 팀 프로필은 못 고친다 — 403")
    void onlyOwnerCanPatchProfile() throws Exception {
        long teamId = idOf(createTeam(null));
        User stranger = createUser("stranger@example.com", "남", "010-2222-2222");

        mockMvc.perform(patch("/api/teams/{id}", teamId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamColor\": \"#000000\"}"))
                .andExpect(status().isForbidden());
    }

    private ResultActions createTeam(String extraFields) throws Exception {
        String body = "{\"name\": \"FC 새벽\", \"region\": \"서울 강서구\","
                + " \"skillLevel\": \"INTERMEDIATE\", \"ageGroup\": \"THIRTIES\","
                + " \"memberCount\": 18";
        if (extraFields != null) {
            body += ", " + extraFields;
        }
        return mockMvc.perform(post("/api/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body + "}"));
    }

    private ResultActions patchTeam(long teamId, String body) throws Exception {
        return mockMvc.perform(patch("/api/teams/{id}", teamId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
