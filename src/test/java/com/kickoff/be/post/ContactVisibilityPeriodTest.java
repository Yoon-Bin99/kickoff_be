package com.kickoff.be.post;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * 경기가 끝난 뒤 연락처·계좌가 보이는 기간 (계약서 §5 「표시 기간」, v1.34.1).
 *
 * <b>이 기간이 동의 문구의 보유·이용 기간이다.</b> 연락처는 "매칭 성사 시 상대 팀 감독에게
 * 제공"에 동의해서 넘어간 값이고(제17조), 경기가 끝나고도 무기한 보이면 동의한 범위를
 * 넘는다. 그래서 기능이 아니라 <b>약속</b>이고, 테스트로 못 박아 둔다.
 *
 * 기본 14일이지만 여기서는 {@code 3} 으로 줄여 둔다 — 기본값으로 쓰면 "지난 경기" 픽스처가
 * 14일 넘게 과거여야 해서, 다른 규칙(지난 글은 목록에서 빠진다 등)과 엉킨다.
 */
@org.springframework.test.context.TestPropertySource(
        properties = "kickoff.match.contact-visible-days=3")
class ContactVisibilityPeriodTest extends IntegrationTestSupport {

    private User author;
    private User applicant;
    private Team authorTeam;
    private Team applicantTeam;

    @BeforeEach
    void setUpTeams() {
        author = createUser("author@example.com", "김작성", "010-1111-1111");
        applicant = createUser("applicant@example.com", "박신청", "010-2222-2222");
        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicant, "FC 노을", "서울 송파구");
    }

    // ── 기간 안 (경기 다음 날)

    @Test
    @DisplayName("기간 안에는 상대 팀에게 연락처와 계좌가 보인다")
    void withinPeriodBothSidesSeeContact() throws Exception {
        MatchPost post = matched(OffsetDateTime.now().minusDays(1));

        // 신청 팀이 보는 작성자 쪽 연락처·계좌
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").isNotEmpty())
                .andExpect(jsonPath("$.payment").isNotEmpty());

        // 작성자가 보는 신청 팀 쪽 연락처
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.contact").isNotEmpty());
    }

    // ── 기간이 지난 뒤

    /**
     * 수락된 매칭이어도 null 이다 — <b>매칭이 끊긴 것이 아니라 표시 기간이 끝난 것</b>이라
     * {@code status} 는 그대로 MATCHED 다. 둘을 섞으면 지난 경기가 "취소된 경기"로 보인다.
     */
    @Test
    @DisplayName("기간이 지나면 상대 팀에게 연락처가 보이지 않는다")
    void afterPeriodContactIsHidden() throws Exception {
        MatchPost post = matched(OffsetDateTime.now().minusDays(4));

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist())
                .andExpect(jsonPath("$.status").value("MATCHED"));
    }

    @Test
    @DisplayName("기간이 지나면 작성자도 상대 팀 연락처를 볼 수 없다")
    void afterPeriodAuthorLosesOpponentContact() throws Exception {
        MatchPost post = matched(OffsetDateTime.now().minusDays(4));

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.contact").doesNotExist());
    }

    /**
     * <b>작성자 본인의 계좌는 예외다</b> (계약서 §5). 자기가 등록한 값을 확인·수정하는
     * 길이라서, 기간으로 막으면 경기 뒤에는 자기 계좌를 고칠 수 없는 글이 생긴다.
     * 제3자 제공이 아니라 본인의 정보를 본인이 보는 것이기도 하다.
     */
    @Test
    @DisplayName("기간이 지나도 작성자 본인의 계좌는 그대로 보인다")
    void authorStillSeesOwnPayment() throws Exception {
        MatchPost post = matched(OffsetDateTime.now().minusDays(4));

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.payment").isNotEmpty())
                .andExpect(jsonPath("$.payment.bankName").isNotEmpty())
                .andExpect(jsonPath("$.payment.accountNumber").isNotEmpty());
    }

    // ── 신청 목록도 같은 규칙 (계약서 §5)

    /**
     * 같은 사실을 두 군데서 보여 주기 때문에 <b>한쪽만 막으면 막은 적이 없는 것과 같다</b> —
     * 글 상세에서 숨겨도 신청 목록에 남아 있으면 연락처는 계속 보인다.
     */
    @Test
    @DisplayName("신청 목록의 연락처·계좌도 기간이 지나면 null 이다")
    void requestListFollowsTheSameRule() throws Exception {
        MatchPost within = matched(OffsetDateTime.now().minusDays(1));
        MatchPost expired = matched(OffsetDateTime.now().minusDays(4));

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                // 글이 목록에 남아 있다 — 필터가 아무것도 못 집었는데 "비었다"로 통과하지 않게
                .andExpect(jsonPath("$[?(@.postId == %d)].status".formatted(expired.getId()))
                        .value(org.hamcrest.Matchers.hasItem("ACCEPTED")))
                .andExpect(jsonPath("$[?(@.postId == %d)].contact".formatted(within.getId()))
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.postId == %d)].payment".formatted(within.getId()))
                        .isNotEmpty())
                .andExpect(jsonPath("$[?(@.postId == %d)].contact".formatted(expired.getId()))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.postId == %d)].payment".formatted(expired.getId()))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    @DisplayName("받은 신청 목록도 기간이 지나면 연락처가 null 이다")
    void receivedListFollowsTheSameRule() throws Exception {
        MatchPost expired = matched(OffsetDateTime.now().minusDays(4));

        mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                // 글이 목록에 남아 있다 — 필터가 아무것도 못 집었는데 "비었다"로 통과하지 않게
                .andExpect(jsonPath("$[?(@.postId == %d)].status".formatted(expired.getId()))
                        .value(org.hamcrest.Matchers.hasItem("ACCEPTED")))
                .andExpect(jsonPath("$[?(@.postId == %d)].contact".formatted(expired.getId()))
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
    }

    // ── 경계

    /**
     * 경계는 <b>"같은 순간까지 보인다"</b>다 — 채팅 보관 기간(§6-1)과 같은 방향이다. 한
     * 서비스 안에서 경계 규칙이 갈리면 어느 쪽이 맞는지 매번 확인해야 한다.
     */
    @Test
    @DisplayName("딱 경계 순간에는 아직 보인다")
    void boundaryMomentIsStillVisible() throws Exception {
        // 지금이 matchAt + 3일에서 1초 전인 글
        MatchPost post = matched(OffsetDateTime.now().minusDays(3).plusSeconds(2));

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.contact").isNotEmpty());
    }

    /** 수락되지 않은 매칭은 기간과 무관하게 애초에 null 이다 (계약서 §5). */
    @Test
    @DisplayName("수락 전에는 기간 안이어도 연락처가 없다")
    void pendingRequestNeverShowsContact() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다",
                OffsetDateTime.now().plusDays(3), true);
        pendingRequest(post, applicantTeam);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    /** 지난 경기의 수락된 매칭 한 벌. */
    private MatchPost matched(OffsetDateTime matchAt) {
        MatchPost post = createPost(authorTeam, "지난 경기 " + matchAt.toLocalDate(),
                matchAt, true);
        acceptedRequest(post, applicantTeam);
        return post;
    }
}
