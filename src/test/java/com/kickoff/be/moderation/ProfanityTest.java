package com.kickoff.be.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.common.ProfanityFilter;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 금칙어 (계약서 §7-2 「금칙어」, v1.35.0).
 *
 * <b>오탐이 더 나쁘다.</b> 걸러야 할 글은 신고로도 걸러지지만, 멀쩡한 글이 막히면
 * 사용자는 무엇이 문제인지 모른 채 글을 못 쓴다 — 그래서 여기 절반이 "이런 말은
 * 걸리면 안 된다"를 본다.
 */
class ProfanityTest extends IntegrationTestSupport {

    @Autowired
    private ProfanityFilter profanityFilter;

    private User user;
    private Team team;

    @BeforeEach
    void setUpUser() {
        user = createUser("user@example.com", "김주장", "010-1111-1111");
        team = createTeam(user, "FC 새벽", "서울 강서구");
    }

    // ── 걸러야 하는 것

    @Test
    @DisplayName("모집글 제목·내용에 욕설이 있으면 400 이다")
    void postIsRejected() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"개새끼들 덤벼라","content":"내용입니다.",
                                 "matchAt":"%s","location":"우장산축구장","region":"서울 강서구"}
                                """.formatted(OffsetDateTime.now().plusDays(7))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'title')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "사용할 수 없는 표현이 포함되어 있습니다.")));
    }

    @Test
    @DisplayName("팀 이름·소개도 검사한다")
    void teamIsRejected() throws Exception {
        User other = createUser("other@example.com", "박감독", "010-2222-2222");

        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"지랄FC","region":"서울 강서구","skillLevel":"INTERMEDIATE",
                                 "ageGroup":"THIRTIES","memberCount":18,
                                 "contactSharingConsent":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'name')]").exists());
    }

    @Test
    @DisplayName("팀 소개 수정도 검사한다")
    void introductionPatchIsRejected() throws Exception {
        mockMvc.perform(patch("/api/teams/{id}", team.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"병신 같은 팀은 사절\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'introduction')]").exists());
    }

    @Test
    @DisplayName("닉네임은 가입과 수정 양쪽에서 검사한다")
    void nicknameIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com","password":"pass1234!",
                                 "nickname":"미친놈","phone":"010-7777-7777",
                                 "consents":{"age14":true,"terms":true,"privacy":true}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')]").exists());

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"씨발러\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')]").exists());
    }

    /** 사이에 공백·점을 끼워 피해 가는 것이 가장 흔한 수법이다. */
    @Test
    @DisplayName("사이에 공백이나 점을 넣어도 걸린다")
    void separatorsDoNotHelp() {
        assertThat(profanityFilter.contains("씨 발")).isTrue();
        assertThat(profanityFilter.contains("개.새.끼")).isTrue();
        assertThat(profanityFilter.contains("지 랄")).isTrue();
    }

    @Test
    @DisplayName("영문 욕설은 대소문자를 가리지 않는다")
    void asciiIsCaseInsensitive() {
        assertThat(profanityFilter.contains("What the FUCK")).isTrue();
        assertThat(profanityFilter.contains("Bitch")).isTrue();
    }

    // ── 걸리면 안 되는 것 (오탐)

    /**
     * 영문을 부분 일치로 두면 "ass" 하나가 class·pass·bass 를 전부 막는다 —
     * 그래서 단어 경계로만 본다.
     */
    @Test
    @DisplayName("영문은 단어 경계로만 본다 — class·pass 는 통과한다")
    void asciiMatchesWholeWordsOnly() {
        assertThat(profanityFilter.contains("first class pitch")).isFalse();
        assertThat(profanityFilter.contains("Pass and move")).isFalse();
        assertThat(profanityFilter.contains("Shittake")).isFalse();
    }

    /** 목록에 "시발"·"죽어라"·"등신"을 넣지 않은 이유가 이것이다. */
    @Test
    @DisplayName("멀쩡한 한국어 문장은 걸리지 않는다")
    void ordinaryKoreanPasses() {
        assertThat(profanityFilter.contains("시발점은 강서구입니다")).isFalse();
        assertThat(profanityFilter.contains("죽어라 뛰는 팀입니다")).isFalse();
        assertThat(profanityFilter.contains("등신대 현수막을 겁니다")).isFalse();
        assertThat(profanityFilter.contains("매너 좋은 팀 환영합니다")).isFalse();
        assertThat(profanityFilter.contains("새벽에 모입니다")).isFalse();
    }

    /** 시드·기존 테스트가 쓰는 문장이 걸리면 그날로 전체가 빨개진다. */
    @Test
    @DisplayName("기존 시드·테스트에 쓰이는 문장은 걸리지 않는다")
    void seedDataPasses() {
        assertThat(profanityFilter.contains("토요일 아침 11인제 상대 구합니다")).isFalse();
        assertThat(profanityFilter.contains("매주 일요일 아침에 모입니다. 11인제로 꾸준히 뜁니다."))
                .isFalse();
        assertThat(profanityFilter.contains("연령대는 높지만 실력은 자신 있습니다.")).isFalse();
        assertThat(profanityFilter.contains("FC 새벽")).isFalse();
        assertThat(profanityFilter.contains("인천 스트라이커즈")).isFalse();
    }

    @Test
    @DisplayName("빈 값과 null 은 통과한다 — 다른 검증의 몫이다")
    void blankPasses() {
        assertThat(profanityFilter.contains(null)).isFalse();
        assertThat(profanityFilter.contains("")).isFalse();
        assertThat(profanityFilter.contains("   ")).isFalse();
    }

    /** 이미 저장된 것에는 소급하지 않는다 (계약서 §7-2) — 저장할 때만 본다. */
    @Test
    @DisplayName("이미 저장된 글은 조회에 영향을 받지 않는다")
    void existingContentIsNotReprocessed() throws Exception {
        com.kickoff.be.post.entity.MatchPost saved = createPost(team, "개새끼 들어간 옛 글");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/posts/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("개새끼 들어간 옛 글"));
    }
}
