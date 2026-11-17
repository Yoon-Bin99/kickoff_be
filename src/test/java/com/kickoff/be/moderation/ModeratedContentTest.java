package com.kickoff.be.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.review.entity.Review;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 운영 조치로 가려진 콘텐츠 (계약서 §7-2 「가려진 콘텐츠」, v1.35.0).
 *
 * <b>가린다고 없애지 않는다.</b> 정보통신망법 제44조의2 제2항이 임시조치 사실을 이용자가
 * 알 수 있게 하라고 해서, 글은 410 으로 조치 문구를 돌려주고 리뷰는 자리 표시로 남는다.
 * 404 로 답하면 "없는 글"과 구별되지 않아 그 요구를 비껴간다.
 *
 * 조치를 거는 API(신고·운영자)는 다음 단계라, 여기서는 엔티티에 직접 걸고 <b>응답이
 * 어떻게 달라지는지</b>만 본다.
 */
class ModeratedContentTest extends IntegrationTestSupport {

    private User author;
    private User other;
    private Team authorTeam;
    private Team otherTeam;

    @BeforeEach
    void setUpTeams() {
        author = createUser("author@example.com", "김작성", "010-1111-1111");
        other = createUser("other@example.com", "박타인", "010-2222-2222");
        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        otherTeam = createTeam(other, "FC 노을", "서울 송파구");
    }

    // ── 모집글

    @Test
    @DisplayName("가려진 글은 목록에서 빠진다")
    void hiddenPostIsNotListed() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .doesNotExist());
    }

    /**
     * <b>작성자에게는 열린다</b> (계약서 §7-2). 자기 글이 왜 안 보이는지, 언제까지인지를
     * 알아야 이의를 제기할 수 있다 — 그 길까지 막으면 조치가 통보 없는 삭제가 된다.
     */
    @Test
    @DisplayName("가려진 글의 상세는 작성자에게만 열리고 moderation 이 실린다")
    void authorStillSeesHiddenPost() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderation.status").value("HIDDEN"))
                .andExpect(jsonPath("$.moderation.until").isNotEmpty());
    }

    @Test
    @DisplayName("다른 사람이 열면 410 이고 message 가 표시 문구다")
    void othersGet410() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONTENT_MODERATED"))
                .andExpect(jsonPath("$.message")
                        .value("신고로 임시로 가려진 게시물입니다."));
    }

    @Test
    @DisplayName("비로그인에게도 410 이다 — 404 로 숨기지 않는다")
    void anonymousGets410() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONTENT_MODERATED"));
    }

    @Test
    @DisplayName("삭제된 글의 문구는 다르고 until 은 없다")
    void removedPostHasItsOwnMessage() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        transactionTemplate.executeWithoutResult(status -> {
            MatchPost found = postRepository.findById(post.getId()).orElseThrow();
            found.removeByOperator();
        });

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("운영 정책에 따라 삭제된 게시물입니다."));
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.moderation.status").value("REMOVED"))
                .andExpect(jsonPath("$.moderation.until").doesNotExist());
    }

    /** 고칠 수 있으면 조치가 아니다 — 가려진 내용을 지우고 다시 올리는 길이 열린다. */
    @Test
    @DisplayName("작성자의 수정·삭제도 410 이다")
    void authorCannotEditOrDelete() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(patch("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"제목을 바꿔 봅니다\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONTENT_MODERATED"));

        mockMvc.perform(delete("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("가려진 글에는 새 신청을 넣을 수 없다 — 409 POST_NOT_OPEN")
    void hiddenPostTakesNoRequests() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_NOT_OPEN"));
    }

    @Test
    @DisplayName("내 글 목록에는 moderation 과 함께 나온다")
    void myPostListCarriesModeration() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        hide(post);

        mockMvc.perform(get("/api/posts/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)].moderation.status"
                        .formatted(post.getId()))
                        .value(org.hamcrest.Matchers.hasItem("HIDDEN")));
    }

    /**
     * <b>기한이 지나면 저절로 풀린다</b> (계약서 §7-2). 운영자가 30일 안에 결론을 내지
     * 못했는데도 계속 가려져 있으면, 결론 없는 신고 하나로 글이 영영 사라진다.
     */
    @Test
    @DisplayName("기한이 지난 임시 가림은 풀린 것으로 본다")
    void expiredHideIsOver() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        expireHide(post);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moderation").doesNotExist());
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .exists());
    }

    // ── 리뷰

    /**
     * 리뷰는 <b>목록에서 빼지 않는다</b>. 통째로 사라지면 받은 팀도 쓴 팀도 무슨 일이
     * 있었는지 알 수 없고, 그 자리에 조치 사실을 남기라는 요구도 지키지 못한다.
     */
    @Test
    @DisplayName("가려진 리뷰는 자리 표시로 남는다 — 별점·내용이 null")
    void hiddenReviewStaysAsPlaceholder() throws Exception {
        Review review = reviewOf(5, "아주 좋았습니다.");
        hide(review);

        mockMvc.perform(get("/api/teams/{id}/reviews", authorTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].rating").doesNotExist())
                .andExpect(jsonPath("$.content[0].comment").doesNotExist())
                .andExpect(jsonPath("$.content[0].moderation.status").value("HIDDEN"));
        assertThat(review.getId()).isNotNull();
    }

    /** 신고로 가려진 별점이 평점에 남아 있으면, 조치가 오히려 제재가 된다. */
    @Test
    @DisplayName("가려진 리뷰는 평점·리뷰 수 집계에서 빠진다")
    void hiddenReviewIsNotCounted() throws Exception {
        Review review = reviewOf(5, "아주 좋았습니다.");

        mockMvc.perform(get("/api/teams/{id}", authorTeam.getId()))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.averageRating").value(5.0));

        hide(review);

        mockMvc.perform(get("/api/teams/{id}", authorTeam.getId()))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    // ── 팀 소개

    @Test
    @DisplayName("가려진 팀 소개는 null 이고 introductionModeration 이 채워진다")
    void hiddenIntroductionIsNulled() throws Exception {
        transactionTemplate.executeWithoutResult(status -> teamRepository
                .findById(authorTeam.getId()).orElseThrow()
                .hideIntroduction(OffsetDateTime.now()));

        mockMvc.perform(get("/api/teams/{id}", authorTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").doesNotExist())
                .andExpect(jsonPath("$.introductionModeration.status").value("HIDDEN"))
                .andExpect(jsonPath("$.name").value("FC 새벽"));
    }

    @Test
    @DisplayName("가려진 동안 소개 수정은 410 이다")
    void hiddenIntroductionCannotBeEdited() throws Exception {
        transactionTemplate.executeWithoutResult(status -> teamRepository
                .findById(authorTeam.getId()).orElseThrow()
                .hideIntroduction(OffsetDateTime.now()));

        mockMvc.perform(patch("/api/teams/{id}", authorTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"새로 씁니다\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONTENT_MODERATED"));
    }

    /**
     * 삭제는 다르다 — 운영자가 지운 것은 그 소개이지 소개를 쓸 자격이 아니다. 새 소개를
     * 쓰면 표시가 사라지는 것이 계약이 정한 복구 경로다.
     */
    @Test
    @DisplayName("삭제된 소개는 새로 쓰면 표시가 사라진다")
    void removedIntroductionCanBeRewritten() throws Exception {
        transactionTemplate.executeWithoutResult(status -> teamRepository
                .findById(authorTeam.getId()).orElseThrow()
                .removeIntroductionByOperator());

        mockMvc.perform(get("/api/teams/{id}", authorTeam.getId()))
                .andExpect(jsonPath("$.introduction").doesNotExist())
                .andExpect(jsonPath("$.introductionModeration.status").value("REMOVED"));

        mockMvc.perform(patch("/api/teams/{id}", authorTeam.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"다시 씁니다\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").value("다시 씁니다"))
                .andExpect(jsonPath("$.introductionModeration").doesNotExist());
    }

    // ── 헬퍼

    private void hide(MatchPost post) {
        transactionTemplate.executeWithoutResult(status -> postRepository
                .findById(post.getId()).orElseThrow().hide(OffsetDateTime.now()));
    }

    private void hide(Review review) {
        transactionTemplate.executeWithoutResult(status -> reviewRepository
                .findById(review.getId()).orElseThrow().hide(OffsetDateTime.now()));
    }

    /** 기한이 이미 지난 임시 가림 — 30일 전에 걸린 모양이다. */
    private void expireHide(MatchPost post) {
        transactionTemplate.executeWithoutResult(status -> postRepository
                .findById(post.getId()).orElseThrow()
                .hide(OffsetDateTime.now().minusDays(31)));
    }

    /** 다른 팀이 우리 팀에게 남긴 리뷰 한 건. */
    private Review reviewOf(int rating, String comment) throws Exception {
        MatchPost post = createPost(otherTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest request = acceptedRequest(post, authorTeam);
        mockMvc.perform(post("/api/requests/{id}/review", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": %d, \"comment\": \"%s\"}"
                                .formatted(rating, comment)))
                .andExpect(status().isCreated());
        return reviewRepository.findAll().get(0);
    }
}
