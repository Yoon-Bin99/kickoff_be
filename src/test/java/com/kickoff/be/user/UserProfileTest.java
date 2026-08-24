package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * PATCH /api/users/me 와 전화번호 규칙 (계약서 §3, §4).
 * 소셜 가입자는 전화번호 없이 시작하는데, 팀을 만들려면 그 값이 있어야 한다.
 */
class UserProfileTest extends IntegrationTestSupport {

    private static final String TEAM_BODY = """
            {"name":"FC 새벽","region":"서울 강서구","skillLevel":"INTERMEDIATE",
             "ageGroup":"THIRTIES","memberCount":15}
            """;

    @Test
    @DisplayName("전화번호가 없으면 팀을 만들 수 없다 — 400 PHONE_REQUIRED")
    void teamCreationRequiresPhone() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜가입자");

        createTeam(social)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PHONE_REQUIRED"));

        assertThat(teamRepository.count()).isZero();
    }

    @Test
    @DisplayName("전화번호를 채우면 곧바로 팀을 만들 수 있다")
    void teamCreationSucceedsAfterPhoneIsFilled() throws Exception {
        User social = createUserWithoutPhone("social@example.com", "소셜가입자");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(social))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-1234-5678"));

        createTeam(social).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("닉네임만 보내면 전화번호는 그대로 둔다 — 둘 다 optional 이다")
    void patchLeavesOmittedFieldsAlone() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"바뀐주장\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("바뀐주장"))
                .andExpect(jsonPath("$.phone").value("010-1111-1111"));

        // 빈 본문이어도 아무것도 바뀌지 않는다
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("바뀐주장"))
                .andExpect(jsonPath("$.phone").value("010-1111-1111"));
    }

    @Test
    @DisplayName("형식이 틀린 값은 400 — 검증 규칙은 signup 과 같다")
    void patchValidatesFormat() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"01012345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phone"));

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"김\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("nickname"));
    }

    @Test
    @DisplayName("비로그인은 프로필을 고칠 수 없다 — 401")
    void anonymousCannotPatch() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"010-1234-5678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이메일로만 가입한 사용자는 authProviders 가 빈 배열이고 phone 이 채워져 있다")
    void emailOnlyUserHasNoProviders() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("010-1111-1111"))
                .andExpect(jsonPath("$.authProviders").isArray())
                .andExpect(jsonPath("$.authProviders").isEmpty());
    }

    private ResultActions createTeam(User user) throws Exception {
        return mockMvc.perform(post("/api/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(TEAM_BODY));
    }
}
