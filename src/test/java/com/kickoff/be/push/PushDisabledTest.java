package com.kickoff.be.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * PUSH_ENABLED 가 꺼져 있으면 아무것도 나가지 않는다 (계약서 §8, 기본값 false).
 *
 * 개발자가 로컬에서 실기기 토큰이 등록된 DB 를 만졌을 때 실제 알림이 날아가지 않게 하는
 * 안전장치라, 토큰이 멀쩡히 등록돼 있어도 발송이 없어야 한다.
 */
@TestPropertySource(properties = "kickoff.push.enabled=false")
class PushDisabledTest extends IntegrationTestSupport {

    @Test
    @DisplayName("꺼져 있으면 토큰이 등록돼 있어도 발송하지 않는다")
    void sendsNothingWhenDisabled() throws Exception {
        User author = createUser("author@example.com", "김주장", "010-1111-1111");
        User applicant = createUser("b@example.com", "이감독", "010-2222-2222");
        Team authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(applicant, "마포 유나이티드", "서울 마포구");
        MatchPost post = createPost(authorTeam, "토요일 아침 풋살 상대 구합니다");

        mockMvc.perform(put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]\"}"))
                .andExpect(status().isNoContent());
        pushClient.reset();

        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"마포에서 갑니다\"}"))
                .andExpect(status().isCreated());

        assertThat(pushClient.sent()).isEmpty();
    }
}
