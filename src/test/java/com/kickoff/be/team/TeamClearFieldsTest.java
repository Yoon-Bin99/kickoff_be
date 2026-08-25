package com.kickoff.be.team;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 팀 PATCH 지우기 규칙 (계약서 §4, v1.5.1).
 *
 * 지울 수 있는 건 홈 구장과 소개뿐이다. 나머지는 팀을 식별하거나 매칭 필터에 쓰이는 값이라
 * 빈 상태가 의미를 갖지 않는다 — 그런 필드에 null 이 오면 조용히 무시하지 않고 400 을 낸다.
 */
class TeamClearFieldsTest extends IntegrationTestSupport {

    private User owner;
    private long teamId;

    @BeforeEach
    void setUpTeam() throws Exception {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        teamId = idOf(mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "FC 새벽", "region": "서울 강서구",
                                 "homeGround": "강서구민운동장", "skillLevel": "INTERMEDIATE",
                                 "ageGroup": "THIRTIES", "memberCount": 18,
                                 "introduction": "매주 토요일 오전 7시에 모입니다."}"""))
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("홈 구장을 null 로 보내면 지워진다")
    void homeGroundCanBeCleared() throws Exception {
        patchTeam("{\"homeGround\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeGround").isEmpty());

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(jsonPath("$.homeGround").isEmpty())
                // 같이 지워지면 안 된다
                .andExpect(jsonPath("$.introduction").value("매주 토요일 오전 7시에 모입니다."));
    }

    @Test
    @DisplayName("소개를 null 로 보내면 지워진다")
    void introductionCanBeCleared() throws Exception {
        patchTeam("{\"introduction\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").isEmpty())
                .andExpect(jsonPath("$.homeGround").value("강서구민운동장"));
    }

    @Test
    @DisplayName("지울 수 없는 필드에 null 을 보내면 400")
    void requiredFieldsRejectExplicitNull() throws Exception {
        for (String field : new String[] {
                "name", "region", "skillLevel", "ageGroup", "memberCount"}) {
            patchTeam("{\"" + field + "\": null}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        }

        mockMvc.perform(get("/api/teams/{id}", teamId))
                .andExpect(jsonPath("$.name").value("FC 새벽"));
    }

    @Test
    @DisplayName("이름만 바꾸는 PATCH 는 홈 구장·소개를 건드리지 않는다")
    void absentFieldsAreUntouched() throws Exception {
        patchTeam("{\"name\": \"FC 노을\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FC 노을"))
                .andExpect(jsonPath("$.homeGround").value("강서구민운동장"))
                .andExpect(jsonPath("$.introduction").value("매주 토요일 오전 7시에 모입니다."));
    }

    @Test
    @DisplayName("빈 문자열은 지우기가 아니다 — 길이 규칙으로 거절")
    void emptyStringIsNotAClear() throws Exception {
        patchTeam("{\"homeGround\": \"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("homeGround"));
    }

    private ResultActions patchTeam(String body) throws Exception {
        return mockMvc.perform(patch("/api/teams/{id}", teamId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
