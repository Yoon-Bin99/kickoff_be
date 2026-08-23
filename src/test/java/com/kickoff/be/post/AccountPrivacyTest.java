package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 계좌가 새지 않는지 기계적으로 확인한다.
 *
 * jsonPath 로 payment 만 보면 부족하다. 나중에 누가 응답 DTO 에 계좌 필드를 추가하면
 * payment 는 그대로인 채로 다른 자리에서 새기 때문이다. 그래서 응답 본문 문자열 전체에
 * 계좌번호가 들어 있는지를 직접 본다.
 */
class AccountPrivacyTest extends IntegrationTestSupport {

    private User author;
    private User applicant;
    private User stranger;
    private MatchPost post;

    @BeforeEach
    void setUpPost() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicant = createUser("applicant@example.com", "이감독", "010-2222-2222");
        stranger = createUser("stranger@example.com", "정주장", "010-3333-3333");

        Team authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        createTeam(applicant, "마포 유나이티드", "서울 마포구");
        createTeam(stranger, "성남 레인저스", "경기 성남시");

        post = createPost(authorTeam, "토요일 아침 풋살 상대 구합니다");
    }

    @Test
    @DisplayName("목록에는 어떤 경우에도 계좌가 실리지 않는다")
    void listNeverCarriesAccount() throws Exception {
        assertNoAccount(mockMvc.perform(get("/api/posts")));
        assertNoAccount(mockMvc.perform(get("/api/posts").param("size", "50")));
        assertNoAccount(mockMvc.perform(get("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))));
        // 작성자 본인의 글 목록에도 실리지 않는다 — 상세에서만 payment 로 준다
        assertNoAccount(mockMvc.perform(get("/api/posts/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))));
    }

    @Test
    @DisplayName("비로그인과 제3자의 상세에는 계좌가 없다")
    void detailHidesAccountFromOutsiders() throws Exception {
        assertNoAccount(mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.payment").isEmpty()));

        assertNoAccount(mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(jsonPath("$.payment").isEmpty()));
    }

    @Test
    @DisplayName("수락되기 전의 신청 팀에게는 아직 계좌가 안 보인다")
    void pendingApplicantCannotSeeAccount() throws Exception {
        apply(applicant);

        assertNoAccount(mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$.myRequestStatus").value("PENDING"))
                .andExpect(jsonPath("$.payment").isEmpty()));
    }

    @Test
    @DisplayName("작성자 본인은 자기 계좌를 상세에서 확인할 수 있다 (수정 화면용)")
    void authorSeesOwnAccount() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.payment.bankName").value(BANK))
                .andExpect(jsonPath("$.payment.accountHolder").value(ACCOUNT_HOLDER))
                .andExpect(jsonPath("$.payment.depositAmount").value(DEPOSIT_AMOUNT))
                // 수락된 신청이 없으면 입금 확인은 아직 false
                .andExpect(jsonPath("$.payment.depositPaid").value(false));
    }

    @Test
    @DisplayName("수락된 뒤에야 신청 팀에게 계좌가 열린다")
    void acceptedApplicantSeesAccount() throws Exception {
        long requestId = apply(applicant);
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER));

        // 무관한 제3자는 매칭이 끝난 뒤에도 못 본다
        assertNoAccount(mockMvc.perform(get("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(stranger))));
    }

    @Test
    @DisplayName("신청 목록에서는 작성자에게 payment 대신 depositPaid 만 준다")
    void requestListGivesPaymentOnlyToApplicant() throws Exception {
        long requestId = apply(applicant);
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        assertNoAccount(mockMvc.perform(get("/api/requests/received")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$[0].payment").isEmpty())
                .andExpect(jsonPath("$[0].depositPaid").value(false)));

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payment.accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    @DisplayName("입금 확인은 양쪽 응답에 반영된다")
    void confirmDepositIsVisibleToBothSides() throws Exception {
        long requestId = apply(applicant);
        mockMvc.perform(post("/api/requests/{id}/accept", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/requests/{id}/confirm-deposit", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositPaid").value(true));

        mockMvc.perform(get("/api/requests/sent")
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(jsonPath("$[0].payment.depositPaid").value(true));
    }

    @Test
    @DisplayName("수락되지 않은 신청에 입금 확인을 하면 409 REQUEST_NOT_ACCEPTED")
    void confirmDepositRequiresAcceptedRequest() throws Exception {
        long requestId = apply(applicant);

        mockMvc.perform(post("/api/requests/{id}/confirm-deposit", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REQUEST_NOT_ACCEPTED"));
    }

    @Test
    @DisplayName("입금액을 넣으면서 계좌를 빠뜨리면 어느 필드가 빠졌는지 알려준다")
    void depositWithoutAccountIsRejected() throws Exception {
        String body = """
                {"title":"계좌 없는 입금글","content":"거절되어야 한다",
                 "matchAt":"%s","location":"강서구민운동장","region":"서울 강서구",
                 "fieldType":"FUTSAL","depositAmount":50000}
                """.formatted(java.time.OffsetDateTime.now().plusDays(3));

        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field")
                        .value(org.hamcrest.Matchers.containsInAnyOrder(
                                "bankName", "accountNumber", "accountHolder")));
    }

    /** 응답 본문 어디에도 계좌번호·은행명이 없어야 한다. */
    private void assertNoAccount(ResultActions actions) throws Exception {
        String body = bodyOf(actions.andExpect(status().isOk()));
        assertThat(body)
                .as("응답에 계좌가 새어나갔다: %s", body)
                .doesNotContain(ACCOUNT_NUMBER)
                .doesNotContain(BANK)
                .doesNotContain("accountNumber")
                .doesNotContain("bankName");
    }

    private long apply(User user) throws Exception {
        return idOf(mockMvc.perform(post("/api/posts/{id}/requests", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"신청합니다\"}"))
                .andExpect(status().isCreated()));
    }
}
