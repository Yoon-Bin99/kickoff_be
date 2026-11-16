package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.common.crypto.AccountEncryptionBackfill;
import com.kickoff.be.common.crypto.FieldCipher;
import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 모집글의 입금 계좌는 암호화해 저장한다 (계약서 §5).
 *
 * <b>이 파일이 지키는 것은 두 가지다.</b> DB 에 평문 계좌가 한 건도 없는 것, 그리고
 * <b>그럼에도 §5 의 노출 규칙·검증·PATCH 병합이 하나도 바뀌지 않은 것</b>. 뒤쪽이 더
 * 깨지기 쉽다 — 저장 방식을 바꾸면서 조용히 응답이 달라지는 일이 흔하다.
 */
class AccountEncryptionTest extends IntegrationTestSupport {

    @Autowired
    private FieldCipher cipher;
    @Autowired
    private AccountEncryptionBackfill backfill;

    private User author;
    private User applicant;
    private Team authorTeam;
    private Team applicantTeam;

    @BeforeEach
    void setUpActors() {
        author = createUser("author@example.com", "김주장", "010-1111-1111");
        applicant = createUser("applicant@example.com", "이감독", "010-2222-2222");
        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
        applicantTeam = createTeam(applicant, "마포 유나이티드", "서울 마포구");
    }

    // ── 저장

    /** 이 테스트가 통과하지 않으면 암호화를 넣은 이유가 없다. */
    @Test
    @DisplayName("DB 에는 평문 계좌가 한 건도 없다")
    void databaseHoldsNoPlainTextAccount() {
        createPost(authorTeam, "토요일 아침 상대 구합니다");

        List<Object[]> rows = rawAccountRows();

        assertThat(rows).isNotEmpty();
        for (Object[] row : rows) {
            assertThat((String) row[0]).as("은행명").startsWith(FieldCipher.PREFIX);
            assertThat((String) row[1]).as("계좌번호").startsWith(FieldCipher.PREFIX);
            assertThat((String) row[2]).as("예금주").startsWith(FieldCipher.PREFIX);
            assertThat(String.join("|", (String) row[0], (String) row[1], (String) row[2]))
                    .doesNotContain(ACCOUNT_NUMBER)
                    .doesNotContain(BANK)
                    .doesNotContain(ACCOUNT_HOLDER);
        }
    }

    @Test
    @DisplayName("API 로 만든 글도 같은 경로를 탄다")
    void accountCreatedThroughApiIsEncrypted() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated());

        assertThat(rawAccountRows()).allSatisfy(row ->
                assertThat((String) row[1]).startsWith(FieldCipher.PREFIX));
    }

    @Test
    @DisplayName("계좌가 없는 글은 세 칸이 null 로 남는다 — 빈 암호문을 만들지 않는다")
    void postWithoutAccountKeepsNulls() {
        createPost(authorTeam, "계좌 없는 글", java.time.OffsetDateTime.now().plusDays(7), false);

        assertThat(rawAccountRows()).allSatisfy(row -> {
            assertThat(row[0]).isNull();
            assertThat(row[1]).isNull();
            assertThat(row[2]).isNull();
        });
    }

    // ── 동작 불변 (계약서 §5)

    /**
     * 저장 방식이 바뀌어도 <b>볼 수 있는 사람은 그대로</b>여야 한다. 수락된 신청 팀과
     * 작성자 본인뿐이다.
     */
    @Test
    @DisplayName("수락된 신청 팀은 평문 계좌를 그대로 받는다")
    void acceptedApplicantSeesPlainAccount() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        acceptedRequest(post, applicantTeam);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.bankName").value(BANK))
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.payment.accountHolder").value(ACCOUNT_HOLDER))
                .andExpect(jsonPath("$.payment.depositAmount").value(DEPOSIT_AMOUNT));
    }

    @Test
    @DisplayName("작성자 본인도 자기 계좌를 평문으로 본다")
    void authorSeesOwnAccount() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author)))
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    @DisplayName("제3자와 비로그인에게는 payment 가 없다")
    void othersSeeNoPayment() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        User outsider = createUser("outsider@example.com", "정주장", "010-3333-3333");

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(jsonPath("$.payment").doesNotExist());
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    /**
     * 길이 제한은 <b>평문 기준</b>이다(계약서 §5 — 20/30/20자). 칸이 varchar(255) 로 넓어진
     * 것은 암호문 때문이고, 그 숫자가 입력 상한이 되면 계약이 깨진다.
     */
    @Test
    @DisplayName("길이 검증은 평문 기준 그대로다 — 31자 계좌번호는 400")
    void validationStillUsesPlainTextLimits() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody().replace(ACCOUNT_NUMBER, "1".repeat(31))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("PATCH 로 금액만 고쳐도 계좌가 살아 있다 — 병합 규칙 그대로")
    void patchingAmountKeepsStoredAccount() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        mockMvc.perform(patch("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositAmount\":70000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.depositAmount").value(70000))
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER));
    }

    @Test
    @DisplayName("PATCH 로 계좌를 바꾸면 새 값이 다시 암호화된다")
    void patchedAccountIsReEncrypted() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        mockMvc.perform(patch("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountNumber\":\"110-999-888777\",\"paymentShareConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.accountNumber").value("110-999-888777"));

        String stored = (String) rawAccountRows().get(0)[1];
        assertThat(stored).startsWith(FieldCipher.PREFIX).doesNotContain("110-999");
        assertThat(cipher.decrypt(stored)).isEqualTo("110-999-888777");
    }

    @Test
    @DisplayName("입금액을 지우면 계좌 세 칸도 함께 null 이 된다")
    void clearingAmountClearsAccount() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        mockMvc.perform(patch("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depositAmount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment").doesNotExist());

        assertThat(rawAccountRows()).allSatisfy(row -> assertThat(row[1]).isNull());
    }

    // ── 옛 평문 행 옮기기

    /**
     * 암호화를 넣기 전에 저장된 행이 남아 있을 수 있다. 읽을 때 평문을 그대로 돌려주므로
     * <b>옮기기 전에도 글은 정상으로 보인다</b> — 그래서 옮기기가 조용히 안 돌아도 모를 수
     * 있다. 이 테스트가 그 자리를 지킨다.
     */
    @Test
    @DisplayName("평문으로 남은 행을 암호화 형식으로 옮긴다")
    void backfillEncryptsLegacyPlainTextRows() {
        MatchPost post = createPost(authorTeam, "옛 글");
        storePlainText(post.getId());
        assertThat((String) rawAccountRows().get(0)[1]).isEqualTo(ACCOUNT_NUMBER);

        backfill.run(null);

        String stored = (String) rawAccountRows().get(0)[1];
        assertThat(stored).startsWith(FieldCipher.PREFIX);
        assertThat(cipher.decrypt(stored)).isEqualTo(ACCOUNT_NUMBER);
    }

    /** 재배포마다 도는 코드라, 두 번째부터는 아무것도 하지 않아야 한다. */
    @Test
    @DisplayName("두 번 돌려도 이중 암호화되지 않는다")
    void backfillIsIdempotent() {
        MatchPost post = createPost(authorTeam, "옛 글");
        storePlainText(post.getId());

        backfill.run(null);
        String once = (String) rawAccountRows().get(0)[1];
        backfill.run(null);
        String twice = (String) rawAccountRows().get(0)[1];

        assertThat(twice).isEqualTo(once);
        assertThat(cipher.decrypt(twice)).isEqualTo(ACCOUNT_NUMBER);
    }

    @Test
    @DisplayName("옮긴 뒤에도 수락된 신청 팀이 계좌를 그대로 본다")
    void migratedAccountIsStillReadableThroughApi() throws Exception {
        MatchPost post = createPost(authorTeam, "옛 글");
        MatchRequest accepted = acceptedRequest(post, applicantTeam);
        storePlainText(post.getId());

        backfill.run(null);

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.accountNumber").value(ACCOUNT_NUMBER));
        assertThat(accepted.getId()).isNotNull();
    }

    // ── 거들기

    /** 변환기를 건너뛰고 저장된 날것을 본다 — 평문이 남았는지는 이렇게만 확인된다. */
    @SuppressWarnings("unchecked")
    private List<Object[]> rawAccountRows() {
        return transactionTemplate.execute(status -> entityManager
                .createNativeQuery("""
                        select bank_name, account_number, account_holder
                          from match_posts order by id
                        """)
                .getResultList());
    }

    /** 암호화 이전 상태를 만든다. 변환기를 거치지 않으려고 네이티브로 쓴다. */
    private void storePlainText(Long postId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("""
                        update match_posts
                           set bank_name = ?1, account_number = ?2, account_holder = ?3
                         where id = ?4
                        """)
                .setParameter(1, BANK)
                .setParameter(2, ACCOUNT_NUMBER)
                .setParameter(3, ACCOUNT_HOLDER)
                .setParameter(4, postId)
                .executeUpdate());
    }

    private String createBody() {
        return """
                {"title":"토요일 아침 상대 구합니다","content":"내용입니다.",
                 "matchAt":"%s","location":"우장산축구장","region":"서울 강서구",
                 "depositAmount":%d,"bankName":"%s","accountNumber":"%s","accountHolder":"%s",
                 "paymentShareConfirmed":true}
                """.formatted(java.time.OffsetDateTime.now().plusDays(7),
                DEPOSIT_AMOUNT, BANK, ACCOUNT_NUMBER, ACCOUNT_HOLDER);
    }
}
