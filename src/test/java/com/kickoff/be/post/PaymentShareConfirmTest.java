package com.kickoff.be.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 계좌를 적을 때 받는 확인 (계약서 §5 — {@code paymentShareConfirmed}, v1.34.0).
 *
 * <b>동의가 아니라 확인이다.</b> 입금 계좌는 작성자 본인이 자기 글에 적는 값이라 제3자 제공
 * 동의를 받을 일이 아니다. 다만 그 값이 <b>수락된 상대 팀 감독에게 보인다</b>는 사실은
 * 적는 사람이 알고 있어야 한다 — 모르고 적었다가 나중에 알게 되는 쪽이 사고다.
 *
 * 그래서 동의 기록({@code user_consents})에 남기지 않고 그 글의 확인 시각으로 남긴다.
 */
class PaymentShareConfirmTest extends IntegrationTestSupport {

    private User author;
    private Team authorTeam;

    @BeforeEach
    void setUpAuthor() {
        author = createUser("author@example.com", "김작성", "010-1111-1111");
        authorTeam = createTeam(author, "FC 새벽", "서울 강서구");
    }

    // ── 글 작성

    @Test
    @DisplayName("확인하고 계좌를 적으면 글이 만들어지고 확인 시각이 남는다")
    void confirmedCreateSucceeds() throws Exception {
        createPostWith("\"paymentShareConfirmed\":true").andExpect(status().isCreated());

        assertThat(postRepository.findAll())
                .singleElement()
                .satisfies(post -> assertThat(post.getPaymentShareConfirmedAt()).isNotNull());
    }

    @Test
    @DisplayName("확인하지 않고 계좌를 적으면 400 이고 field 가 그 칸을 가리킨다")
    void unconfirmedCreateIsRejected() throws Exception {
        createPostWith("\"paymentShareConfirmed\":false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'paymentShareConfirmed')]")
                        .exists());

        assertThat(postRepository.findAll()).as("글 자체가 만들어지지 않는다").isEmpty();
    }

    /** 누락도 거부와 같이 본다 — 구버전 앱이 보내는 모양이고, 통과시키면 규칙이 없는 것과 같다. */
    @Test
    @DisplayName("필드를 아예 안 보내도 400 이다")
    void missingFieldIsRejected() throws Exception {
        createPostWith(null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'paymentShareConfirmed')]")
                        .exists());
    }

    /**
     * <b>계좌가 없으면 묻지 않는다.</b> 묻는 이유가 "상대에게 보인다"는 사실을 알리는
     * 것이라서, 보일 값이 없으면 확인받을 것도 없다 — 무료 경기 글마다 체크박스를
     * 들이밀면 아무도 읽지 않는 확인이 된다.
     */
    @Test
    @DisplayName("계좌 없는 글은 확인을 묻지 않는다")
    void postWithoutAccountNeedsNoConfirmation() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"무료 경기 상대 구합니다","content":"내용입니다.",
                                 "matchAt":"%s","location":"우장산축구장","region":"서울 강서구"}
                                """.formatted(OffsetDateTime.now().plusDays(7))))
                .andExpect(status().isCreated());

        assertThat(postRepository.findAll())
                .singleElement()
                .satisfies(post -> assertThat(post.getPaymentShareConfirmedAt()).isNull());
    }

    // ── 수정

    @Test
    @DisplayName("PATCH 로 계좌를 고칠 때도 확인을 받는다")
    void patchingAccountNeedsConfirmation() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        patchPost(post, """
                {"bankName":"신한","accountNumber":"110-123-456789","accountHolder":"김작성"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'paymentShareConfirmed')]")
                        .exists());
    }

    /**
     * 은행명 한 칸만 고치는 요청에도 묻는다. 세 칸 중 하나만 달라져도 <b>상대에게 보이는
     * 계좌 한 벌이 달라진다</b> — 바뀐 칸의 개수로 판단할 일이 아니다.
     */
    @Test
    @DisplayName("은행명만 고쳐도 확인을 받는다")
    void patchingOneFieldStillNeedsConfirmation() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"bankName\":\"신한\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'paymentShareConfirmed')]")
                        .exists());
    }

    @Test
    @DisplayName("확인하면 PATCH 가 되고 확인 시각이 새로 찍힌다")
    void confirmedPatchSucceeds() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        patchPost(post, """
                {"bankName":"신한","accountNumber":"110-123-456789","accountHolder":"김작성",
                 "paymentShareConfirmed":true}
                """).andExpect(status().isOk());

        assertThat(postRepository.findById(post.getId()).orElseThrow()
                .getPaymentShareConfirmedAt()).isNotNull();
    }

    /** 계좌를 건드리지 않는 수정은 그대로다 — 제목만 고치는 PATCH 가 대부분이다. */
    @Test
    @DisplayName("계좌를 건드리지 않는 PATCH 는 확인을 묻지 않는다")
    void patchingOtherFieldsNeedsNoConfirmation() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"title\":\"제목만 고칩니다\"}").andExpect(status().isOk());
    }

    /**
     * 입금액만 고치는 것도 묻지 않는다. 금액은 글에 적는 안내이고, 상대에게 넘어가는
     * <b>계좌 번호</b>가 아니다.
     */
    @Test
    @DisplayName("입금액만 고치는 PATCH 도 확인을 묻지 않는다")
    void patchingAmountNeedsNoConfirmation() throws Exception {
        MatchPost post = createPost(authorTeam, "토요일 아침 상대 구합니다");

        patchPost(post, "{\"depositAmount\":20000}").andExpect(status().isOk());
    }

    private ResultActions createPostWith(String confirmField) throws Exception {
        String account = "\"depositAmount\":10000,\"bankName\":\"신한\","
                + "\"accountNumber\":\"110-123-456789\",\"accountHolder\":\"김작성\"";
        String body = """
                {"title":"토요일 아침 상대 구합니다","content":"내용입니다.",
                 "matchAt":"%s","location":"우장산축구장","region":"서울 강서구",
                 %s%s}
                """.formatted(OffsetDateTime.now().plusDays(7), account,
                confirmField == null ? "" : "," + confirmField);
        return mockMvc.perform(post("/api/posts")
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patchPost(MatchPost post, String body) throws Exception {
        return mockMvc.perform(patch("/api/posts/{id}", post.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
