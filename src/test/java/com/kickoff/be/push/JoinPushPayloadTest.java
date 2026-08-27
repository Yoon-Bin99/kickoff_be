package com.kickoff.be.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * 가입 푸시가 실제로 <b>선 위에 실어 보내는 JSON</b>을 본다 (계약서 §4-3·§8).
 *
 * 기존 테스트는 스텁이 받은 PushMessage 객체만 확인했다. 그런데 FE 가 읽는 건 객체가 아니라
 * Expo 로 나가는 JSON 이고, 딥링크는 그 안의 {@code data.teamId} 하나에 달려 있다 —
 * 객체에 값이 있어도 직렬화에서 빠지거나 타입이 바뀌면 앱은 폴백 화면으로 떨어진다.
 * 그 사이를 아무도 안 보고 있어서 여기서 본다.
 */
class JoinPushPayloadTest extends IntegrationTestSupport {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private User owner;
    private User applicant;
    private Team team;

    @BeforeEach
    void setUp() throws Exception {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        applicant = createUser("applicant@example.com", "박멤버", "010-2222-2222");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
        registerPushToken(owner);
        registerPushToken(applicant);
    }

    @Test
    @DisplayName("JOIN_REQUEST_RECEIVED payload 의 data.teamId 가 숫자로 실려 나간다")
    void joinRequestReceivedCarriesNumericTeamId() throws Exception {
        pushClient.reset();
        apply();

        String json = mapper.writeValueAsString(pushClient.last());

        // 딥링크가 딛고 서는 값. 문자열로 나가면 FE 가 숫자 비교에서 놓칠 수 있다.
        assertThat(json).contains("\"teamId\":" + team.getId());
        assertThat(json).contains("\"type\":\"JOIN_REQUEST_RECEIVED\"");
    }

    @Test
    @DisplayName("수락·거절 payload 도 같은 규칙이다")
    void acceptAndRejectFollowTheSameRule() throws Exception {
        long joinId = idOf(apply());

        pushClient.reset();
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/accept",
                        team.getId(), joinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        assertThat(mapper.writeValueAsString(pushClient.last()))
                .contains("\"teamId\":" + team.getId())
                .contains("\"type\":\"JOIN_ACCEPTED\"");

        // 강퇴 후 재신청 → 거절
        long secondJoinId = idOf(kickAndReapply());
        pushClient.reset();
        mockMvc.perform(post("/api/teams/{id}/join-requests/{joinId}/reject",
                        team.getId(), secondJoinId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());
        assertThat(mapper.writeValueAsString(pushClient.last()))
                .contains("\"teamId\":" + team.getId())
                .contains("\"type\":\"JOIN_REJECTED\"");
    }

    @Test
    @DisplayName("Expo 가 받는 네 키가 전부 최상위에 있다")
    void payloadShapeMatchesExpoApi() throws Exception {
        pushClient.reset();
        apply();

        PushMessage message = pushClient.last();
        String json = mapper.writeValueAsString(message);

        // Expo 는 to/title/body/data 를 최상위에서 읽는다. 이름이 바뀌면 조용히 무시된다.
        assertThat(json).contains("\"to\":").contains("\"title\":")
                .contains("\"body\":").contains("\"data\":");
        assertThat(message.data()).containsKeys("type", "teamId");
    }

    private org.springframework.test.web.servlet.ResultActions apply() throws Exception {
        return mockMvc.perform(post("/api/teams/{id}/join", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions kickAndReapply() throws Exception {
        long memberId = com.jayway.jsonpath.JsonPath.parse(bodyOf(
                        mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/api/teams/{id}/members", team.getId()))))
                .read("$[0].id", Integer.class).longValue();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/teams/{id}/members/{memberId}", team.getId(), memberId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNoContent());
        return apply();
    }

    private void registerPushToken(User user) throws Exception {
        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\": \"ExponentPushToken[u" + user.getId() + "]\"}"))
                .andExpect(status().isNoContent());
    }
}
