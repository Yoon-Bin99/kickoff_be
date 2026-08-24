package com.kickoff.be.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 팀이 받은 리뷰의 목록·페이징과 평점 집계 (계약서 §7).
 *
 * 송파 FC 가 세 팀과 지난 경기를 치르고 5·4·4 를 받는다 — 평균 4.4666… 이 아니라
 * 13/3 = 4.333… 이라 계약서의 "소수 첫째 자리 반올림"이 4.3 으로 내려가는 쪽을 확인한다.
 */
class TeamRatingTest extends IntegrationTestSupport {

    private User targetOwner;
    private Team targetTeam;
    private Team saebyeok;
    private Team mapo;
    private Team goyang;

    @BeforeEach
    void setUpReviews() throws Exception {
        targetOwner = createUser("target@example.com", "박캡틴", "010-1111-1111");
        User a = createUser("a@example.com", "김주장", "010-2222-2222");
        User b = createUser("b@example.com", "이감독", "010-3333-3333");
        User c = createUser("c@example.com", "최총무", "010-4444-4444");

        targetTeam = createTeam(targetOwner, "송파 FC", "서울 송파구");
        saebyeok = createTeam(a, "FC 새벽", "서울 강서구");
        mapo = createTeam(b, "마포 유나이티드", "서울 마포구");
        goyang = createTeam(c, "고양 킥커스", "경기 고양시");

        // 세 팀의 지난 경기에 송파 FC 가 신청해 수락된 상태를 만든다
        review(a, pastMatch(saebyeok, "지난 강서 경기"), 5, "매너가 아주 좋았습니다.");
        review(b, pastMatch(mapo, "지난 마포 경기"), 4, "즐거운 경기였습니다.");
        review(c, pastMatch(goyang, "지난 고양 경기"), 4, "다음에 또 뵙겠습니다.");
    }

    @Test
    @DisplayName("averageRating 은 소수 첫째 자리로 반올림된다 — 5·4·4 의 평균 4.333… 은 4.3")
    void averageRatingIsRoundedToOneDecimal() throws Exception {
        mockMvc.perform(get("/api/teams/{id}", targetTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(3))
                .andExpect(jsonPath("$.averageRating").value(4.3));
    }

    @Test
    @DisplayName("리뷰를 한 건도 못 받은 팀은 reviewCount 0, averageRating null")
    void teamWithoutReviewsHasNullAverage() throws Exception {
        // 고양 킥커스는 리뷰를 쓰기만 했지 받지는 않았다
        mockMvc.perform(get("/api/teams/{id}", goyang.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").isEmpty());
    }

    @Test
    @DisplayName("글 상세의 team 에도 평점이 함께 실린다")
    void postDetailCarriesTeamRating() throws Exception {
        MatchPost post = createPost(targetTeam, "송파 다음 주 경기");

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team.reviewCount").value(3))
                .andExpect(jsonPath("$.team.averageRating").value(4.3));
    }

    @Test
    @DisplayName("팀 리뷰 목록은 인증 없이 볼 수 있고, 받은 리뷰만 담긴다")
    void teamReviewListIsPublicAndContainsOnlyReceivedReviews() throws Exception {
        String body = bodyOf(mockMvc.perform(get("/api/teams/{id}/reviews", targetTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(3))));

        // 전부 송파 FC 를 향한 리뷰이고, 쓴 팀은 세 팀이다
        List<Integer> targets = JsonPath.read(body, "$.content[*].targetTeamId");
        assertThat(targets).containsOnly(targetTeam.getId().intValue());
        List<Integer> reviewers = JsonPath.read(body, "$.content[*].reviewerTeam.id");
        assertThat(reviewers).containsExactlyInAnyOrder(saebyeok.getId().intValue(),
                mapo.getId().intValue(), goyang.getId().intValue());
    }

    @Test
    @DisplayName("리뷰 목록은 createdAt 내림차순, 동률이면 id 내림차순이다")
    void teamReviewListIsSortedByCreatedAtThenIdDesc() throws Exception {
        String body = bodyOf(mockMvc.perform(get("/api/teams/{id}/reviews", targetTeam.getId()))
                .andExpect(status().isOk()));

        List<String> createdAt = JsonPath.read(body, "$.content[*].createdAt");
        assertThat(createdAt).isSortedAccordingTo(Comparator.reverseOrder());

        // 세 건이 한 셋업에서 연달아 만들어져 createdAt 이 같은 밀리초로 겹칠 수 있다.
        // 2차 키가 없으면 이 순서가 조회마다 흔들린다 (계약서 §7, v1.2.2).
        List<Integer> ids = JsonPath.read(body, "$.content[*].id");
        assertThat(ids).isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("페이지를 한 건씩 훑어도 중복·누락이 없다 — 무한 스크롤이 안전하려면 정렬이 결정적이어야 한다")
    void pagingIsDeterministic() throws Exception {
        List<Integer> collected = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            String body = bodyOf(mockMvc.perform(get("/api/teams/{id}/reviews", targetTeam.getId())
                            .param("page", String.valueOf(page))
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1))));
            collected.addAll(JsonPath.read(body, "$.content[*].id"));
        }
        assertThat(collected).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("리뷰 목록은 page/size 로 나뉘어 내려간다")
    void teamReviewListIsPaged() throws Exception {
        mockMvc.perform(get("/api/teams/{id}/reviews", targetTeam.getId())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/teams/{id}/reviews", targetTeam.getId())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("없는 팀의 리뷰 목록은 404 TEAM_NOT_FOUND")
    void reviewListOfMissingTeamIsNotFound() throws Exception {
        mockMvc.perform(get("/api/teams/{id}/reviews", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    /** 상대 팀(송파 FC)이 신청해 수락된, 이미 끝난 경기를 만든다. */
    private MatchRequest pastMatch(Team hostTeam, String title) {
        MatchPost post = createPost(hostTeam, title, OffsetDateTime.now().minusDays(3), true);
        return acceptedRequest(post, targetTeam);
    }

    private void review(User reviewer, MatchRequest request, int rating, String comment)
            throws Exception {
        String body = "{\"rating\": %d, \"comment\": \"%s\"}".formatted(rating, comment);
        mockMvc.perform(post("/api/requests/{id}/review", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(reviewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
