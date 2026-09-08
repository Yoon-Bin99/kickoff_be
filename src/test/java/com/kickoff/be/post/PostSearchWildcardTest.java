package com.kickoff.be.post;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 모집글 검색의 와일드카드 처리 (계약서 §5).
 *
 * 팀 검색은 v1.14.0 에서 이 처리를 갖췄는데 <b>모집글 검색에는 빠져 있었다.</b> 그래서
 * {@code keyword=%} 하나로 모든 글이, {@code _} 하나로 한 글자 이상인 글이 전부 나왔다.
 * 에러가 아니라 결과가 너무 많이 나올 뿐이라 화면만 봐서는 "검색이 잘 되네"로 지나친다.
 *
 * 남용 경로이기도 하다 — {@code %%%%%} 같은 입력이 전체 스캔을 유도한다.
 *
 * <b>값 이스케이프와 쿼리의 {@code escape '!'} 절은 짝이다.</b> 한쪽만 있으면 더 나빠진다.
 * 절 없이 값만 감싸면 {@code !} 가 문자 그대로 매칭되어, {@code 50%} 를 찾는 사람이
 * {@code 50!%} 를 찾게 된다. 아래 "! 자체도 검색된다"가 그 짝을 지킨다.
 */
class PostSearchWildcardTest extends IntegrationTestSupport {

    private Team team;

    @BeforeEach
    void setUpTeam() {
        User owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        team = createTeam(owner, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("% 는 리터럴이다 — 전부 보여 달라는 뜻이 아니다")
    void percentIsLiteral() throws Exception {
        post("대관료 100% 지원", "서울 강서구");
        post("주말 상대 구합니다", "서울 마포구");

        // 이스케이프가 없으면 여기서 2건(전체)이 나온다.
        search("%")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("대관료 100% 지원"));
    }

    @Test
    @DisplayName("_ 도 리터럴이다 — 한 글자 아무거나가 아니다")
    void underscoreIsLiteral() throws Exception {
        post("A_B 구장 경기", "서울 강서구");
        post("주말 상대 구합니다", "서울 마포구");

        search("_")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("A_B 구장 경기"));

        // 반대 방향도 본다 — A_B 는 "AB" 로 찾히면 안 된다
        search("ab").andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("이스케이프 문자로 쓴 ! 자체도 검색된다")
    void escapeCharacterItselfIsSearchable() throws Exception {
        post("드디어! 경기합니다", "서울 강서구");
        post("주말 상대 구합니다", "서울 마포구");

        // ! 를 먼저 감싸지 않으면 패턴이 "%!%" 가 되어 % 를 리터럴로 해석해 버린다.
        search("!")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("드디어! 경기합니다"));
    }

    /** 본문도 검색 대상이다 (title 과 content 를 OR 로 본다). 두 LIKE 다 절이 있어야 한다. */
    @Test
    @DisplayName("본문 쪽 LIKE 에도 같은 규칙이 걸린다")
    void contentSideIsEscapedToo() throws Exception {
        postWithContent("보통 제목", "참가비 50% 할인합니다", "서울 강서구");
        post("주말 상대 구합니다", "서울 마포구");

        search("50%")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("보통 제목"));
    }

    /**
     * region 도 같은 규칙이어야 한다. 한 API 안에서 필터마다 규칙이 갈리면, 어느 쪽이
     * 옳은지 알 수 없는 채로 둘 다 의심하게 된다.
     */
    @Test
    @DisplayName("region 도 와일드카드를 리터럴로 다룬다")
    void regionIsEscapedToo() throws Exception {
        post("첫 번째", "서울 강서구");
        post("두 번째", "서울 마포구");

        mockMvc.perform(get("/api/posts").param("region", "%"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private void post(String title, String region) throws Exception {
        postWithContent(title, "잘 부탁드립니다", region);
    }

    private void postWithContent(String title, String content, String region) {
        postRepository.save(com.kickoff.be.post.entity.MatchPost.builder()
                .team(team)
                .title(title)
                .content(content)
                .matchAt(OffsetDateTime.now().plusDays(3))
                .location("구장")
                .region(region)
                .build());
    }

    private ResultActions search(String keyword) throws Exception {
        return mockMvc.perform(get("/api/posts").param("keyword", keyword));
    }
}
