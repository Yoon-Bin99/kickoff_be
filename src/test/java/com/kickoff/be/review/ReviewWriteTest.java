package com.kickoff.be.review;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 리뷰 작성 규칙 (계약서 §7). 네 조건을 전부 만족해야 쓸 수 있다 —
 * 수락된 매칭 / 끝난 경기 / 당사자 / 아직 안 씀.
 */
class ReviewWriteTest extends IntegrationTestSupport {

    private User author;
    private User applicant;
    private User outsider;
    private Team authorTeam;
    private Team applicantTeam;
    private MatchPost pastPost;
    private MatchRequest accepted;

    @BeforeEach
    void setUpMatch() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicant = createUser("applicant@example.com", "이감독", "010-2222-2222");
        outsider = createUser("outsider@example.com", "박캡틴", "010-3333-3333");

        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicant, "마포 유나이티드", "서울 마포구");
        createTeam(outsider, "송파 FC", "서울 송파구");

        pastPost = createPost(authorTeam, "지난 주 풋살", OffsetDateTime.now().minusDays(3), true);
        accepted = acceptedRequest(pastPost, applicantTeam);
    }

    @Test
    @DisplayName("글 작성 팀이 쓰면 대상은 신청 팀이 된다 — 대상은 본문이 아니라 서버가 정한다")
    void authorReviewsApplicantTeam() throws Exception {
        writeReview(author, accepted.getId(), 5, "시간 약속 정확했습니다.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(accepted.getId()))
                .andExpect(jsonPath("$.postId").value(pastPost.getId()))
                .andExpect(jsonPath("$.postTitle").value("지난 주 풋살"))
                .andExpect(jsonPath("$.reviewerTeam.id").value(authorTeam.getId()))
                .andExpect(jsonPath("$.reviewerTeam.name").value("FC 새벽"))
                .andExpect(jsonPath("$.targetTeamId").value(applicantTeam.getId()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").value("시간 약속 정확했습니다."));
    }

    @Test
    @DisplayName("신청 팀이 쓰면 대상은 글 작성 팀이 된다 — 같은 매칭에 양 팀이 각각 한 번씩 쓴다")
    void bothTeamsCanReviewTheSameMatch() throws Exception {
        writeReview(author, accepted.getId(), 5, "매너 좋았습니다.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetTeamId").value(applicantTeam.getId()));

        writeReview(applicant, accepted.getId(), 4, "즐거웠습니다.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerTeam.id").value(applicantTeam.getId()))
                .andExpect(jsonPath("$.targetTeamId").value(authorTeam.getId()));

        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("comment 는 optional — 없으면 null 로 저장된다")
    void commentIsOptional() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/review", accepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.comment").isEmpty());
    }

    @Test
    @DisplayName("수락되지 않은 매칭에는 쓸 수 없다 — 409 REVIEW_NOT_AVAILABLE")
    void cannotReviewWhenRequestIsNotAccepted() throws Exception {
        MatchPost anotherPast =
                createPost(authorTeam, "지난 주 다른 경기", OffsetDateTime.now().minusDays(2), true);
        MatchRequest pending = pendingRequest(anotherPast, applicantTeam);

        writeReview(author, pending.getId(), 5, "아직 수락도 안 했습니다.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("아직 열리지 않은 경기에는 쓸 수 없다 — 409 REVIEW_NOT_AVAILABLE")
    void cannotReviewBeforeTheMatchIsPlayed() throws Exception {
        MatchPost futurePost =
                createPost(authorTeam, "다음 주 경기", OffsetDateTime.now().plusDays(5), true);
        MatchRequest futureAccepted = acceptedRequest(futurePost, applicantTeam);

        writeReview(author, futureAccepted.getId(), 5, "경기도 안 했는데요.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_AVAILABLE"));

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("매칭 당사자가 아닌 팀은 쓸 수 없다 — 403 FORBIDDEN")
    void outsiderTeamCannotReview() throws Exception {
        writeReview(outsider, accepted.getId(), 1, "남의 경기에 별점을 남깁니다.")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("팀이 없는 사용자는 어느 매칭의 당사자도 아니다 — 403 FORBIDDEN")
    void userWithoutTeamCannotReview() throws Exception {
        User teamless = createUser("noteam@example.com", "무소속", "010-4444-4444");

        writeReview(teamless, accepted.getId(), 5, "팀이 없습니다.")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("같은 매칭에 두 번은 못 쓴다 — 409 REVIEW_ALREADY_EXISTS")
    void cannotReviewTwiceOnTheSameMatch() throws Exception {
        writeReview(author, accepted.getId(), 5, "첫 번째 리뷰").andExpect(status().isCreated());

        writeReview(author, accepted.getId(), 1, "마음이 바뀌었습니다.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("rating 은 1~5 정수만 받는다 — 0, 6, 누락은 전부 400")
    void ratingMustBeBetweenOneAndFive() throws Exception {
        writeReview(author, accepted.getId(), 0, "0점입니다.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));

        writeReview(author, accepted.getId(), 6, "6점입니다.")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));

        mockMvc.perform(post("/api/requests/{id}/review", accepted.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"평점을 빼먹었습니다.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 신청에 쓰면 404 REQUEST_NOT_FOUND")
    void reviewOnMissingRequestIsNotFound() throws Exception {
        writeReview(author, 999_999L, 5, "없는 매칭입니다.")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("비로그인은 리뷰를 쓸 수 없다 — 401")
    void anonymousCannotReview() throws Exception {
        mockMvc.perform(post("/api/requests/{id}/review", accepted.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("myReviewWritten 은 보는 팀 기준 — 한쪽이 써도 상대는 그대로 false 다")
    void myReviewWrittenIsPerViewingTeam() throws Exception {
        // 쓰기 전에는 양쪽 다 false
        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$[0].myReviewWritten").value(false));
        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$[0].myReviewWritten").value(false));

        writeReview(author, accepted.getId(), 5, "작성 팀만 씁니다.")
                .andExpect(status().isCreated());

        // 쓴 쪽만 true 로 바뀐다
        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$[0].myReviewWritten").value(true));
        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$[0].myReviewWritten").value(false));
    }

    private ResultActions writeReview(User user, long requestId, int rating, String comment)
            throws Exception {
        String body = "{\"rating\": %d, \"comment\": \"%s\"}".formatted(rating, comment);
        return mockMvc.perform(post("/api/requests/{id}/review", requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
