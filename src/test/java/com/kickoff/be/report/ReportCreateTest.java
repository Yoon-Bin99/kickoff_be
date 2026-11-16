package com.kickoff.be.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.report.entity.ReportTargetType;
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
 * 신고를 받는다 (계약서 §7-2 「신고」, v1.35.0).
 *
 * 신고는 <b>스토어 심사 요건이자 법적 창구</b>다(정보통신망법 제44조의2). 그래서 "받아
 * 두고 운영자가 보면 된다"로 끝나지 않는다 — 사유와 대상이 맞아야 운영자가 무엇을 볼지
 * 알 수 있고, 자기 것을 신고하는 길이 열리면 그 자체가 공격 수단이 된다.
 */
class ReportCreateTest extends IntegrationTestSupport {

    private User reporter;
    private User author;
    private Team reporterTeam;
    private Team authorTeam;
    private MatchPost post;

    @BeforeEach
    void setUpTargets() {
        reporter = createUser("reporter@example.com", "김신고", "010-1111-1111");
        author = createUser("author@example.com", "박작성", "010-2222-2222");
        reporterTeam = createTeam(reporter, "FC 새벽", "서울 강서구");
        authorTeam = createTeam(author, "FC 노을", "서울 송파구");
        post = createPost(authorTeam, "토요일 아침 상대 구합니다");
    }

    @Test
    @DisplayName("모집글을 신고하면 201 과 id 가 온다")
    void reportingPostSucceeds() throws Exception {
        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"SPAM","detail":"광고입니다."}
                """.formatted(post.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber());

        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.POST);
            assertThat(saved.getStatus()).isEqualTo(ReportStatus.OPEN);
            assertThat(saved.getReportedUserId()).isEqualTo(author.getId());
        });
    }

    /**
     * 신고 뒤에 글이 고쳐지거나 지워져도 운영자가 볼 것이 있어야 한다 — 그래서 신고
     * 시점의 사본을 함께 남긴다 (계약서 §7-2 운영자 상세).
     */
    @Test
    @DisplayName("신고 시점의 대상 사본이 함께 남는다")
    void targetSnapshotIsStored() throws Exception {
        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                """.formatted(post.getId())).andExpect(status().isCreated());

        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getTargetTitle()).isEqualTo("토요일 아침 상대 구합니다");
            assertThat(saved.getTargetBody()).isNotBlank();
        });
    }

    // ── 사유와 대상의 짝 (계약서 §7-2 표)

    @Test
    @DisplayName("대상에 쓸 수 없는 사유는 400 이다")
    void reasonMustFitTarget() throws Exception {
        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"NO_SHOW"}
                """.formatted(post.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'reason')]").exists());
    }

    /**
     * 본인·자기 팀이면 법정 삭제 요청으로 다뤄야 한다(제44조의2 제1항) — 누구의 권리인지
     * 모르면 그 갈림을 할 수 없다.
     */
    @Test
    @DisplayName("명예훼손은 누구의 권리인지를 받아야 한다")
    void defamationNeedsRightsHolder() throws Exception {
        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"DEFAMATION"}
                """.formatted(post.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'rightsHolder')]").exists());

        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"DEFAMATION","rightsHolder":"MY_TEAM"}
                """.formatted(post.getId())).andExpect(status().isCreated());
    }

    // ── 자기 것

    @Test
    @DisplayName("내 팀의 글은 신고할 수 없다")
    void cannotReportOwnPost() throws Exception {
        MatchPost mine = createPost(reporterTeam, "내가 쓴 글");

        report(reporter, """
                {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                """.formatted(mine.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'targetId')]").exists());
    }

    @Test
    @DisplayName("내 팀도 신고할 수 없다")
    void cannotReportOwnTeam() throws Exception {
        report(reporter, """
                {"targetType":"TEAM","targetId":%d,"reason":"ABUSE"}
                """.formatted(reporterTeam.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 대상은 404 다")
    void missingTargetIs404() throws Exception {
        report(reporter, """
                {"targetType":"POST","targetId":999999,"reason":"SPAM"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));

        report(reporter, """
                {"targetType":"TEAM","targetId":999999,"reason":"ABUSE"}
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_FOUND"));
    }

    // ── 중복과 횟수 제한

    @Test
    @DisplayName("같은 대상을 또 신고하면 409 다")
    void duplicateIsRejected() throws Exception {
        report(reporter, spamPost()).andExpect(status().isCreated());

        report(reporter, spamPost())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_EXISTS"));
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    /**
     * 하루 제한은 <b>지난 24시간</b> 기준이다. 자정 기준이면 23시 59분에 열 건, 1분 뒤에
     * 또 열 건이 들어온다 — 막으려던 일이 그대로 일어난다.
     */
    @Test
    @DisplayName("하루 10건을 넘으면 429 다")
    void dailyLimitIsEnforced() throws Exception {
        for (int i = 0; i < Report.DAILY_LIMIT; i++) {
            MatchPost target = createPost(authorTeam, "글 " + i);
            report(reporter, """
                    {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                    """.formatted(target.getId())).andExpect(status().isCreated());
        }

        report(reporter, spamPost())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("REPORT_RATE_LIMITED"));
    }

    // ── 신고한 사람 화면에서 바로 빠진다 (계약서 §7-2 「신고의 즉시 효과」 1)

    @Test
    @DisplayName("신고하면 내 목록에서는 바로 빠지고 남에게는 그대로다")
    void reportedPostDisappearsForReporterOnly() throws Exception {
        report(reporter, spamPost()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .doesNotExist());

        // 다른 사람과 비로그인에게는 그대로다 — 한 사람의 신고로 모두의 화면에서
        // 사라지면 그것이 공격 수단이 된다
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .exists());
    }

    @Test
    @DisplayName("신고해도 글 상세는 열린다 — 가린 것이 아니라 내 목록에서 뺀 것이다")
    void reportedPostDetailStillOpens() throws Exception {
        report(reporter, spamPost()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts/{id}", post.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter)))
                .andExpect(status().isOk());
    }

    // ── 자동 임시 가림 (계약서 §7-2 「신고의 즉시 효과」 2)

    /**
     * <b>같은 팀의 신고는 한 명으로 센다.</b> 그러지 않으면 한 팀의 감독·관리자·팀원 셋이
     * 달려들어 상대 글을 30일 가릴 수 있다.
     */
    @Test
    @DisplayName("서로 다른 신고자 셋이면 모두에게 가려진다")
    void threeReportersHideIt() throws Exception {
        report(reporter, spamPost()).andExpect(status().isCreated());
        report(secondReporter(), spamPost()).andExpect(status().isCreated());

        // 둘까지는 그대로다
        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .exists());

        report(thirdReporter(), spamPost()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .doesNotExist());
        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CONTENT_MODERATED"));
        assertThat(reportRepository.findAll())
                .allSatisfy(saved -> {
                    assertThat(saved.getStatus()).isEqualTo(ReportStatus.TEMP_HIDDEN);
                    assertThat(saved.isAutoHidden()).isTrue();
                });
    }

    @Test
    @DisplayName("같은 팀 사람 셋이 신고해도 가려지지 않는다 — 한 명으로 센다")
    void sameTeamCountsAsOne() throws Exception {
        User mate = createUser("mate@example.com", "같은팀원", "010-5555-5555");
        User mate2 = createUser("mate2@example.com", "같은팀원둘", "010-6666-6666");
        joinTeam(reporterTeam, mate);
        joinTeam(reporterTeam, mate2);

        report(reporter, spamPost()).andExpect(status().isCreated());
        report(mate, spamPost()).andExpect(status().isCreated());
        report(mate2, spamPost()).andExpect(status().isCreated());

        mockMvc.perform(get("/api/posts"))
                .andExpect(jsonPath("$.content[?(@.id == %d)]".formatted(post.getId()))
                        .exists());
    }

    /** 팀을 통째로 가리면 그 팀과 매칭이 잡힌 상대까지 화면을 잃는다 (계약서 §7-2). */
    @Test
    @DisplayName("팀 신고는 셋이 쌓여도 자동으로 가리지 않는다")
    void teamIsNeverAutoHidden() throws Exception {
        String body = """
                {"targetType":"TEAM","targetId":%d,"reason":"ABUSE"}
                """.formatted(authorTeam.getId());
        report(reporter, body).andExpect(status().isCreated());
        report(secondReporter(), body).andExpect(status().isCreated());
        report(thirdReporter(), body).andExpect(status().isCreated());

        mockMvc.perform(get("/api/teams/{id}", authorTeam.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.introduction").isNotEmpty());
        assertThat(reportRepository.findAll())
                .allSatisfy(saved -> assertThat(saved.getStatus())
                        .isEqualTo(ReportStatus.OPEN));
    }

    // ── 채팅 메시지 (계약서 §7-2·§10)

    @Test
    @DisplayName("채팅 메시지를 신고하면 앞뒤 대화가 사본으로 저장된다")
    void chatReportStoresContext() throws Exception {
        report(reporter, """
                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,"reason":"ABUSE",
                 "chatDisclosureAgreed":true}
                """).andExpect(status().isCreated());

        Report saved = reportRepository.findAll().get(0);
        assertThat(reportChatMessageRepository.findByReportIdOrderBySentAtAsc(saved.getId()))
                .hasSize(3)
                .anySatisfy(copy -> {
                    assertThat(copy.isReported()).isTrue();
                    assertThat(copy.getMessageId()).isEqualTo(45L);
                });
        assertThat(reportContextClient.lastReporterUserId()).isEqualTo(reporter.getId());
    }

    /** 1:1 대화를 운영자가 보게 되는 일이다 — 신고 버튼을 눌렀다는 사실로 갈음하지 않는다. */
    @Test
    @DisplayName("전달 동의 없이는 채팅 메시지를 신고할 수 없다")
    void chatReportNeedsDisclosureConsent() throws Exception {
        report(reporter, """
                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,"reason":"ABUSE"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'chatDisclosureAgreed')]")
                        .exists());
    }

    @Test
    @DisplayName("requestId 가 없으면 400 이다 — 메시지는 방 안에서만 식별된다")
    void chatReportNeedsRequestId() throws Exception {
        report(reporter, """
                {"targetType":"CHAT_MESSAGE","targetId":45,"reason":"ABUSE",
                 "chatDisclosureAgreed":true}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'requestId')]").exists());
    }

    /**
     * 사본이 증거다. 받아 둘 수 없으면 <b>신고를 반쪽으로 접수하지 않는다</b> — 운영자가
     * 열었을 때 볼 것이 없는 신고가 쌓이는 쪽이 나쁘다.
     */
    @Test
    @DisplayName("채팅 서버가 답하지 않으면 503 이고 신고가 저장되지 않는다")
    void chatReportFailsWhenChatServerIsDown() throws Exception {
        reportContextClient.failWith(
                new BusinessException(ErrorCode.CHAT_UPSTREAM_UNAVAILABLE));

        report(reporter, """
                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,"reason":"ABUSE",
                 "chatDisclosureAgreed":true}
                """)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAT_UPSTREAM_UNAVAILABLE"));
        assertThat(reportRepository.findAll()).isEmpty();
    }

    /** 그 방의 당사자인지는 채팅 서버가 판정한다 (계약서 §10 의 검사 순서). */
    @Test
    @DisplayName("채팅 서버가 거절하면 그 뜻이 그대로 나간다")
    void chatServerRejectionIsPassedThrough() throws Exception {
        reportContextClient.failWith(new BusinessException(ErrorCode.FORBIDDEN));

        report(reporter, """
                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,"reason":"ABUSE",
                 "chatDisclosureAgreed":true}
                """)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void reportNeedsLogin() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spamPost()))
                .andExpect(status().isUnauthorized());
    }

    // ── 헬퍼

    private String spamPost() {
        return """
                {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                """.formatted(post.getId());
    }

    private ResultActions report(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/reports")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private User secondReporter() {
        User user = createUser("second@example.com", "두번째", "010-3333-3333");
        createTeam(user, "두번째 팀", "서울 강남구");
        return user;
    }

    private User thirdReporter() {
        User user = createUser("third@example.com", "세번째", "010-4444-4444");
        createTeam(user, "세번째 팀", "서울 노원구");
        return user;
    }

    private void joinTeam(Team team, User user) {
        teamMemberRepository.save(com.kickoff.be.team.entity.TeamMember.builder()
                .team(team)
                .user(user)
                .name(user.getNickname())
                .build());
    }
}
