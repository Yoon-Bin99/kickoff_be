package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 모집글 삭제 (계약서 §5, v1.31.3).
 *
 * <b>신청이 하나라도 달린 글은 지울 수 없다.</b> 그 전까지는 막지도 지우지도 않았다 —
 * 외래 키에 걸려 500 이 났고, 작성자는 "삭제가 안 된다"만 보고 이유를 알 수 없었으며
 * 신청이 붙은 글은 영영 못 지웠다. 조용히 지우는 쪽으로 고치지 않은 것은 사용자 결정이다:
 * 거절·철회된 신청도 상대 팀의 이력이고, 리뷰·전적이 그 신청을 참조한다.
 */
class PostDeleteTest extends IntegrationTestSupport {

    private User author;
    private User outsider;
    private User applicant;
    private Team authorTeam;
    private Team applicantTeam;
    private MatchPost post;

    @BeforeEach
    void setUpActors() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        outsider = createUser("outsider@example.com", "정주장", "010-2222-2222");
        applicant = createUser("applicant@example.com", "이감독", "010-3333-3333");
        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(outsider, "성남 레인저스", "경기 성남시");
        applicantTeam = createTeam(applicant, "마포 유나이티드", "서울 마포구");
        post = createPost(authorTeam, "토요일 아침 상대 구합니다");
    }

    @Test
    @DisplayName("신청이 없는 글은 지워진다")
    void postWithoutRequestsIsDeleted() throws Exception {
        deleteBy(author).andExpect(status().isNoContent());

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    /**
     * <b>상태를 가리지 않는다</b>는 것이 이 기능의 전부다. "거절된 신청쯤은 괜찮겠지"로
     * 새면, 그 신청을 참조하는 상대 팀의 리뷰·전적이 함께 끊긴다 — 우리가 지운 것은
     * 우리 글인데 사라지는 것은 남의 화면이다.
     */
    @ParameterizedTest(name = "{0} 신청이 달린 글은 지울 수 없다")
    @EnumSource(RequestStatus.class)
    void postWithAnyRequestIsRejected(RequestStatus status) throws Exception {
        requestWith(status);

        deleteBy(author)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_HAS_REQUESTS"))
                .andExpect(jsonPath("$.message")
                        .value("신청 이력이 있는 글은 삭제할 수 없어요(거절·취소된 신청 포함). "
                                + "더 받지 않으려면 마감으로 바꿔 주세요."));
    }

    @Test
    @DisplayName("막힌 삭제는 글도 신청도 건드리지 않는다")
    void rejectedDeleteChangesNothing() throws Exception {
        MatchRequest request = requestWith(RequestStatus.PENDING);

        deleteBy(author).andExpect(status().isConflict());

        assertThat(postRepository.findById(post.getId())).isPresent();
        assertThat(requestRepository.findById(request.getId())).isPresent();
    }

    /**
     * 권한이 신청 검사보다 먼저다 (계약서 §5 검사 순서). 뒤집으면 제3자가 403/409 의
     * 차이만으로 <b>남의 글에 신청이 있는지</b>를 알아낼 수 있다 — §6-1·§7 과 같은 규칙이다.
     */
    @Test
    @DisplayName("남의 글은 신청이 달려 있어도 403 이다 — 409 가 아니다")
    void authorCheckComesFirst() throws Exception {
        requestWith(RequestStatus.PENDING);

        deleteBy(outsider)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("신청이 없어도 남의 글은 403 이다")
    void outsiderCannotDeleteEmptyPost() throws Exception {
        deleteBy(outsider)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** 없는 글이 권한보다 먼저다 — 남의 글 id 를 넣어 존재 여부를 떠보는 길이 없다. */
    @Test
    @DisplayName("없는 글은 404 다")
    void unknownPostIsNotFound() throws Exception {
        mockMvc.perform(delete("/api/posts/{id}", 99999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/posts/{id}", post.getId()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 신청을 철회해도 행은 남는다({@code CANCELED}). 그래서 "철회했으니 이제 지워지겠지"가
     * 통하지 않는다 — 계약이 상태를 가리지 않기로 한 결과이고, 안내가 가리키는 길은
     * 삭제가 아니라 마감이다.
     */
    @Test
    @DisplayName("신청을 철회해도 글은 여전히 지울 수 없다")
    void canceledRequestStillBlocks() throws Exception {
        requestWith(RequestStatus.CANCELED);

        deleteBy(author)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POST_HAS_REQUESTS"));
    }

    /**
     * <b>화면과 규칙이 어긋나 보이는 자리</b>(계약서 §5, v1.31.4). `requestCount` 는 살아
     * 있는 신청(대기·수락)만 세는데 삭제를 막는 기준은 모든 상태다 — 거절된 신청만 남은
     * 글은 <b>"신청 0건"으로 보이면서 지워지지 않는다.</b>
     *
     * 그래서 안내 문구가 "신청이 있는 글"이 아니라 "신청 <b>이력</b>이 있는 글(거절·취소된
     * 신청 포함)"이다. 이 테스트가 둘을 한자리에서 묶어 둔다 — 세는 범위를 한쪽만 고치면
     * 여기가 깨진다.
     */
    @Test
    @DisplayName("신청 0건으로 보이는 글도 거절 이력이 있으면 409 다")
    void postShowingZeroRequestsCanStillBeBlocked() throws Exception {
        requestWith(RequestStatus.REJECTED);

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestCount").value(0));

        deleteBy(author)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("거절·취소된 신청 포함")));
    }

    private MatchRequest requestWith(RequestStatus status) {
        MatchRequest request = pendingRequest(post, applicantTeam);
        transactionTemplate.executeWithoutResult(tx -> entityManager
                .createQuery("update MatchRequest r set r.status = :status where r.id = :id")
                .setParameter("status", status)
                .setParameter("id", request.getId())
                .executeUpdate());
        return request;
    }

    private ResultActions deleteBy(User user) throws Exception {
        return mockMvc.perform(delete("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
