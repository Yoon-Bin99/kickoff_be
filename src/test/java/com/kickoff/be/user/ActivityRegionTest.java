package com.kickoff.be.user;

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
 * 주요 활동 지역 (계약서 §2·§3, v1.6.0).
 *
 * 핵심은 <b>null 이 "미설정"이 아니라 "전국"</b>이라는 점이다. 그래서 안 보내는 것도,
 * 명시적으로 지우는 것도 정상 상태다 — 어느 쪽도 에러가 아니고, FE 홈 목록이 지역 필터
 * 없이 뜬다는 뜻이다. 값 자체는 BE 가 판단하지 않고 길이만 본다.
 */
class ActivityRegionTest extends IntegrationTestSupport {

    @Test
    @DisplayName("가입할 때 보내면 저장되고 응답에 실려 나간다")
    void signupStoresActivityRegion() throws Exception {
        signup("seoul@example.com", "\"activityRegion\": \"서울\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.activityRegion").value("서울"));
    }

    @Test
    @DisplayName("가입할 때 안 보내면 null — 전국이다")
    void signupWithoutActivityRegionIsNull() throws Exception {
        signup("nowhere@example.com", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.activityRegion").isEmpty());
    }

    @Test
    @DisplayName("PATCH 로 설정하고 다른 값으로 바꿀 수 있다")
    void activityRegionCanBeSetAndChanged() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");

        patchMe(user, "{\"activityRegion\": \"서울\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").value("서울"));

        patchMe(user, "{\"activityRegion\": \"경기\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").value("경기"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.activityRegion").value("경기"));
    }

    @Test
    @DisplayName("명시적 null 을 보내면 지워진다 — 전국으로 복귀")
    void explicitNullClearsActivityRegion() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");
        patchMe(user, "{\"activityRegion\": \"서울\"}").andExpect(status().isOk());

        patchMe(user, "{\"activityRegion\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activityRegion").isEmpty());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.activityRegion").isEmpty());
    }

    @Test
    @DisplayName("활동 지역을 안 보내는 PATCH 는 기존 값을 지우지 않는다")
    void absentActivityRegionIsUntouched() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");
        patchMe(user, "{\"activityRegion\": \"서울\"}").andExpect(status().isOk());

        // 닉네임만 바꾸는 흔한 요청이다. 여기서 지역이 지워지면 사용자는 이유도 모른 채
        // 홈 화면이 전국으로 바뀐 걸 보게 된다 — 좌표 때 실제로 낸 종류의 사고다.
        patchMe(user, "{\"nickname\": \"김주장2\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("김주장2"))
                .andExpect(jsonPath("$.activityRegion").value("서울"));
    }

    @Test
    @DisplayName("20자를 넘으면 400 — 가입과 수정 양쪽 다")
    void tooLongActivityRegionIsRejected() throws Exception {
        String tooLong = "가".repeat(21);

        signup("long@example.com", "\"activityRegion\": \"" + tooLong + "\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("activityRegion"));

        User user = createUser("kim@example.com", "김주장", "010-1111-1111");
        patchMe(user, "{\"activityRegion\": \"" + tooLong + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("activityRegion"));

        // 경계값 20자는 통과한다
        patchMe(user, "{\"activityRegion\": \"" + "가".repeat(20) + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("닉네임·전화번호는 null 로 지울 수 없다 — 400")
    void nicknameAndPhoneCannotBeCleared() throws Exception {
        User user = createUser("kim@example.com", "김주장", "010-1111-1111");

        for (String field : new String[] {"nickname", "phone"}) {
            patchMe(user, "{\"" + field + "\": null}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value(field));
        }

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.nickname").value("김주장"))
                .andExpect(jsonPath("$.phone").value("010-1111-1111"));
    }

    private ResultActions signup(String email, String extraFields) throws Exception {
        String body = "{\"email\": \"" + email + "\", \"password\": \"pass1234!\","
                + " \"nickname\": \"새사용자\", \"phone\": \"010-9999-9999\"";
        if (extraFields != null) {
            body += ", " + extraFields;
        }
        return mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body + "}"));
    }

    private ResultActions patchMe(User user, String body) throws Exception {
        return mockMvc.perform(patch("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
