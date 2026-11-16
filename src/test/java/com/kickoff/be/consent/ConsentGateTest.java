package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 동의를 마치지 않은 계정은 서버가 막는다 (계약서 §3 「동의」, v1.34.0).
 *
 * <b>막는 것이 이 개편의 알맹이다.</b> 전에는 FE 가 게이트를 걸었다 — 앱을 업데이트하지
 * 않은 사용자, API 를 직접 부르는 사용자에게는 게이트가 없는 것과 같았다.
 *
 * 검사는 시큐리티 인가 규칙으로 걸려 있다. 그 자리가 정확히 계약의 "인증이 필요한 API"라서,
 * 인증이 선택인 조회 API 는 그 위의 permitAll 에서 걸러져 여기 오지 않는다 — 아래
 * {@code optionalAuthReadsAreNotBlocked} 가 그 경계를 지킨다.
 */
class ConsentGateTest extends IntegrationTestSupport {

    private User pending;
    private User settled;

    @BeforeEach
    void setUpUsers() {
        // 소셜 최초 로그인의 모양 — 동의 기록이 없다
        pending = createUserWithoutConsents("카카오가입자", "010-9999-9999");
        settled = createUser("ok@example.com", "김주장", "010-1111-1111");
    }

    /**
     * 403 이고 401 이 아닌 것이 중요하다. 401 이면 FE 는 refresh 를 타고 실패하면 로그인
     * 화면으로 보내는데, 여기서 필요한 것은 동의 화면이다 — 로그인은 이미 돼 있다.
     */
    @Test
    @DisplayName("동의가 필요한 계정은 인증 API 에서 403 CONSENT_REQUIRED 다")
    void authenticatedApiIsBlocked() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("동의를 마친 계정은 그대로 통과한다")
    void settledAccountPasses() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(settled)))
                .andExpect(status().isOk());
    }

    /**
     * 검사 순서는 인증(401) → 동의(403) → 그 밖의 검증이다 (계약서 §3). 뒤집으면 토큰 없는
     * 요청이 403 을 받고, FE 는 "동의하면 된다"로 읽어 로그인하지 않은 사용자를 동의 화면에
     * 세운다.
     */
    @Test
    @DisplayName("토큰이 없으면 403 이 아니라 401 이다")
    void unauthenticatedStillGets401() throws Exception {
        mockMvc.perform(get("/api/users/me/teams"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ── 동의 전에도 되는 여섯 (계약서 §3)

    @Test
    @DisplayName("동의 화면이 쓰는 네 경로는 열려 있다")
    void consentScreenPathsAreExempt() throws Exception {
        // 무엇을 받아야 하는지 읽는다
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true));

        // 동의 화면에 머무는 동안 access 가 만료될 수 있다 — 본문이 비어 400 이지만
        // 동의로 막히지는 않는다(403 이 아니다)
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/users/me/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isNoContent());
    }

    /**
     * <b>동의하지 않고 떠날 길</b>이다. 막히면 동의가 강요가 된다 — 동의하지 않으면 탈퇴도
     * 못 하는 계정이 생긴다.
     */
    @Test
    @DisplayName("탈퇴와 그 앞의 비밀번호 확인은 열려 있다")
    void leavingIsExempt() throws Exception {
        User emailUser = createUserWithoutConsents("이메일가입자", "010-8888-8888");
        // 비밀번호가 있는 계정으로 바꿔 둔다 — verify-password 가 의미를 가지려면 필요하다
        mockMvc.perform(post("/api/users/me/verify-password")
                        .header(HttpHeaders.AUTHORIZATION, bearer(emailUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass1234!\"}"))
                // 소셜 계정이라 비밀번호가 없어 400 이지만, <b>403 은 아니다</b>
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(emailUser)))
                .andExpect(status().isNoContent());
    }

    /**
     * 예외 목록은 <b>경로와 메서드가 모두</b> 맞아야 한다. 경로만 보면 같은 주소의 다른
     * 메서드가 함께 열린다 — {@code PATCH /api/users/me}(프로필 수정)가 그 예다.
     */
    @Test
    @DisplayName("예외 경로의 다른 메서드는 막힌다")
    void exemptionIsMethodSpecific() throws Exception {
        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"바꾼이름\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    /**
     * 인증이 선택인 조회 API 는 막지 않는다 (계약서 §3). 토큰을 들고 모집글 목록을 보는
     * 것은 동의 화면에 세울 일이 아니다 — 비로그인도 보는 화면이다.
     */
    @Test
    @DisplayName("인증이 선택인 조회는 토큰이 있어도 막지 않는다")
    void optionalAuthReadsAreNotBlocked() throws Exception {
        Team team = createTeam(settled, "FC 새벽", "서울 강서구");
        MatchPost post = createPost(team, "토요일 아침 상대 구합니다");

        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/holidays?year=2026")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("동의를 마치면 바로 통과한다")
    void passesRightAfterConsenting() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/users/me/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isOk());
        assertThat(consentRepository.findByUserId(pending.getId()))
                .extracting(c -> c.getConsentType())
                .containsExactlyInAnyOrder(ConsentType.AGE14, ConsentType.TERMS,
                        ConsentType.PRIVACY);
    }

    /**
     * <b>로그인은 동의 전에도 된다.</b> 막히면 동의 화면에 닿을 길이 사라진다 — 토큰이
     * 없으면 {@code PUT /api/users/me/consents} 도 부를 수 없다. 시드의
     * {@code consent@example.com} 이 그 경로를 로컬에서 밟는 계정이다.
     *
     * 지금은 {@code /api/auth/**} 가 {@code permitAll} 이라 이 규칙까지 오지 않는다.
     * 이 단언은 그 사실을 못 박아 둔다 — 검사를 필터로 옮기는 날 가장 먼저 깨질 자리다.
     */
    @Test
    @DisplayName("동의 기록이 없어도 로그인은 된다")
    void loginIsNotBlocked() throws Exception {
        userRepository.save(com.kickoff.be.user.entity.User.builder()
                .email("pending@example.com")
                .password(passwordEncoder.encode("pass1234!"))
                .nickname("동의대기")
                .phone("010-6666-6666")
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pending@example.com\",\"password\":\"pass1234!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.consentRequired").value(true));
    }

    /**
     * 운영자 계정도 일반 계정과 같은 규칙이다 (supervisor 확인). 문의함은 인증이 필요한
     * API 라 동의 전에는 403 이다 — 운영자라고 예외를 두면 그 계정만 동의 없이 쓰이게 된다.
     */
    @Test
    @DisplayName("운영자 API 도 같은 규칙으로 막힌다")
    void operatorApiFollowsTheSameRule() throws Exception {
        User operator = createUserWithoutConsents("운영자", "010-7777-7777");

        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }
}
