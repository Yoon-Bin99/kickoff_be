package com.kickoff.be.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * TeamSummary 의 평점 (계약서 §2, v1.10.0).
 *
 * v1.2.0 에서 "목록 카드 변경 최소화"를 이유로 뺐던 결정을 번복한 것이라, 확인할 게 두
 * 가지다.
 *
 * 1. <b>TeamSummary 가 실리는 모든 자리</b>에 값이 채워지는가. 한 자리라도 빠뜨리면
 *    같은 팀이 홈에서는 별 4.5 인데 매칭관리 탭에서는 "평가 없음"으로 나온다 — 에러가
 *    아니라 조용히 틀린 값이다
 * 2. 목록에서 <b>N+1 이 나지 않는가</b>. 계약서가 배치를 못박은 이유이고, 느려질 뿐
 *    결과는 맞아서 테스트 없이는 드러나지 않는다
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class TeamSummaryRatingTest extends IntegrationTestSupport {

    @Autowired
    private SessionFactory sessionFactory;

    private User postOwner;
    private User applicantOwner;
    private Team postTeam;
    private Team applicantTeam;
    private MatchRequest accepted;

    @BeforeEach
    void setUpRatedTeams() {
        postOwner = createUser("post@example.com", "김주장", "010-1111-1111");
        applicantOwner = createUser("applicant@example.com", "이감독", "010-2222-2222");
        postTeam = createTeam(postOwner, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicantOwner, "마포 유나이티드", "서울 마포구");

        MatchPost past = createPost(postTeam, "지난 경기", OffsetDateTime.now().minusDays(3), false);
        accepted = acceptedRequest(past, applicantTeam);
        // 신청 팀이 글 작성 팀을 평가한다 → postTeam 이 평점을 갖는다
        review(applicantOwner, 4);
    }

    @Test
    @DisplayName("홈 목록 카드에 평점이 실린다")
    void homeListCarriesRating() throws Exception {
        createPost(postTeam, "새 모집글", OffsetDateTime.now().plusDays(3), false);

        mockMvc.perform(get("/api/posts").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].team.averageRating").value(4.0))
                .andExpect(jsonPath("$.content[0].team.reviewCount").value(1));
    }

    @Test
    @DisplayName("신청 목록의 양 팀 모두에 실린다 — 평가 없는 팀은 null·0")
    void requestListCarriesBothTeams() throws Exception {
        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postTeam.averageRating").value(4.0))
                .andExpect(jsonPath("$[0].postTeam.reviewCount").value(1))
                // 신청 팀은 아직 평가받은 적이 없다
                .andExpect(jsonPath("$[0].applicantTeam.averageRating").isEmpty())
                .andExpect(jsonPath("$[0].applicantTeam.reviewCount").value(0));

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantOwner)))
                .andExpect(jsonPath("$[0].postTeam.averageRating").value(4.0));
    }

    @Test
    @DisplayName("리뷰 목록의 작성 팀에도 실린다")
    void reviewListCarriesReviewerTeam() throws Exception {
        // postTeam 이 applicantTeam 을 평가 → 그 리뷰의 reviewerTeam 은 postTeam(평점 4.0)
        review(postOwner, 5);

        mockMvc.perform(get("/api/teams/{id}/reviews", applicantTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewerTeam.averageRating").value(4.0))
                .andExpect(jsonPath("$.content[0].reviewerTeam.reviewCount").value(1));
    }

    @Test
    @DisplayName("내 팀 목록에도 실린다")
    void myTeamsCarryRating() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(postOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team.averageRating").value(4.0))
                .andExpect(jsonPath("$[0].team.reviewCount").value(1));
    }

    @Test
    @DisplayName("신청 직후 응답에도 실린다")
    void createRequestResponseCarriesRating() throws Exception {
        MatchPost open = createPost(postTeam, "모집 중", OffsetDateTime.now().plusDays(4), false);

        mockMvc.perform(post("/api/posts/{id}/requests", open.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicantOwner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"신청합니다\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postTeam.averageRating").value(4.0))
                .andExpect(jsonPath("$.applicantTeam.reviewCount").value(0));
    }

    @Test
    @DisplayName("목록이 길어져도 평점 조회는 한 번뿐이다 — N+1 이 아니다")
    void ratingLookupDoesNotGrowWithPageSize() throws Exception {
        // 팀 6개가 각각 글을 하나씩 가진 목록을 만든다
        for (int i = 0; i < 6; i++) {
            User owner = createUser("owner" + i + "@example.com", "주장" + i, "010-5555-000" + i);
            Team team = createTeam(owner, "팀" + i, "서울 강서구");
            createPost(team, "글" + i, OffsetDateTime.now().plusDays(i + 1), false);
        }

        long oneTeam = queryCountOf(() -> mockMvc.perform(
                get("/api/posts").param("size", "1")).andExpect(status().isOk()));
        long manyTeams = queryCountOf(() -> mockMvc.perform(
                get("/api/posts").param("size", "10")).andExpect(status().isOk()));

        // 카드가 늘어도 쿼리는 거의 그대로여야 한다. 팀마다 조회가 나가면 그 차이가
        // 카드 수만큼 벌어진다 — 여유를 두되 선형 증가는 잡아낸다.
        assertThat(manyTeams - oneTeam)
                .as("카드 1개와 10개의 쿼리 수 차이 (배치가 깨지면 팀 수만큼 늘어난다)")
                .isLessThanOrEqualTo(2);
    }

    private long queryCountOf(ThrowingRunnable action) throws Exception {
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();
        action.run();
        return statistics.getPrepareStatementCount();
    }

    private void review(User reviewer, int rating) {
        try {
            mockMvc.perform(post("/api/requests/{id}/review", accepted.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"rating\": " + rating + ", \"comment\": \"좋았습니다\"}"))
                    .andExpect(status().isCreated());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
