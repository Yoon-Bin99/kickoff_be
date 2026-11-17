package com.kickoff.be.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.entity.ReportStatus;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.support.entity.SupportSender;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 신고함과 운영자 조치 (계약서 §7-2 「운영자 API」, v1.35.0·v1.35.1).
 *
 * <b>조치와 통지는 함께 일어난다.</b> 정보통신망법 제44조의2 제2항이 조치 사실을 알리라고
 * 한다 — 가려졌는데 아무도 모르는 상태는 조치가 아니라 사고다. 그래서 여기 대부분의
 * 테스트가 "무엇이 가려졌나"와 "누가 무엇을 들었나"를 함께 본다.
 */
class AdminReportTest extends IntegrationTestSupport {

    private User operator;
    private User reporter;
    private User author;
    private Team authorTeam;
    private MatchPost post;

    @BeforeEach
    void setUpReport() throws Exception {
        operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        reporter = createUser("reporter@example.com", "김신고", "010-1111-1111");
        author = createUser("author@example.com", "박작성", "010-2222-2222");
        createTeam(reporter, "FC 새벽", "서울 강서구");
        authorTeam = createTeam(author, "FC 노을", "서울 송파구");
        post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        report(reporter);
    }

    // ── 목록·상세

    @Test
    @DisplayName("신고함 목록은 처리 전 건을 돌려준다")
    void listReturnsPendingReports() throws Exception {
        admin(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].targetType").value("POST"))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.content[0].reporterCount").value(1))
                .andExpect(jsonPath("$.content[0].autoHidden").value(false))
                .andExpect(jsonPath("$.content[0].hiddenUntil").doesNotExist());
    }

    /**
     * <b>목록의 칸이 같은 층에 펼쳐져 나온다</b> (계약서 §7-2, v1.37.2). 묶음에 넣었더니
     * FE 가 계약 문구대로 읽은 자리가 전부 비었다 — 운영자 상세 화면의 id·사유·상태가
     * 통째로 안 보였다.
     */
    @Test
    @DisplayName("상세에는 신고 시점의 사본과 양쪽 당사자가 같은 층에 담긴다")
    void detailCarriesSnapshotAndParties() throws Exception {
        Long reportId = reportRepository.findAll().get(0).getId();

        admin(get("/api/admin/reports/{id}", reportId))
                .andExpect(status().isOk())
                // ReportSummary 의 칸이 중첩 없이 그대로 온다
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.targetType").value("POST"))
                .andExpect(jsonPath("$.reason").value("SPAM"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reporterCount").value(1))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.target.title").value("토요일 아침 상대 구합니다"))
                .andExpect(jsonPath("$.target.content").isNotEmpty())
                .andExpect(jsonPath("$.reporter.nickname").value("김신고"))
                .andExpect(jsonPath("$.reported.nickname").value("박작성"))
                .andExpect(jsonPath("$.resolution").doesNotExist());
    }

    /** 대상 종류마다 채워지는 칸이 다르다 (계약서 §7-2 표, v1.37.2). */
    @Test
    @DisplayName("팀 신고의 사본은 name·introduction 에 담긴다")
    void teamTargetUsesItsOwnFields() throws Exception {
        reportRepository.deleteAll();
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"TEAM","targetId":%d,"reason":"ABUSE"}
                                """.formatted(authorTeam.getId())))
                .andExpect(status().isCreated());
        Long reportId = reportRepository.findAll().get(0).getId();

        admin(get("/api/admin/reports/{id}", reportId))
                .andExpect(jsonPath("$.target.name").value("FC 노을"))
                .andExpect(jsonPath("$.target.introduction").isNotEmpty())
                .andExpect(jsonPath("$.target.title").doesNotExist())
                .andExpect(jsonPath("$.target.content").doesNotExist())
                .andExpect(jsonPath("$.target.messages").isEmpty());
    }

    /** 채팅 사본은 §6-1 ChatMessage 모양 그대로다 — 채팅 화면 컴포넌트를 그대로 쓴다. */
    @Test
    @DisplayName("채팅 신고의 사본은 ChatMessage 모양이고 senderUserId 는 없다")
    void chatTargetUsesChatMessageShape() throws Exception {
        reportRepository.deleteAll();
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,
                                 "reason":"ABUSE","chatDisclosureAgreed":true}
                                """))
                .andExpect(status().isCreated());
        Long reportId = reportRepository.findAll().get(0).getId();

        admin(get("/api/admin/reports/{id}", reportId))
                .andExpect(jsonPath("$.target.reportedMessageId").value(45))
                .andExpect(jsonPath("$.target.messages", org.hamcrest.Matchers.hasSize(3)))
                .andExpect(jsonPath("$.target.messages[0].id").isNumber())
                .andExpect(jsonPath("$.target.messages[0].type").value("TEXT"))
                .andExpect(jsonPath("$.target.messages[0].senderTeamId").isNumber())
                .andExpect(jsonPath("$.target.messages[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.target.messages[0].senderUserId").doesNotExist())
                .andExpect(jsonPath("$.target.messages[0].reported").doesNotExist())
                // 다섯 칸만이다 (v1.37.3) — requestId 는 신고 건이 이미 들고 있다
                .andExpect(jsonPath("$.target.messages[0].requestId").doesNotExist())
                .andExpect(jsonPath("$.target.messages[0].length()").value(5));
    }

    /** 시각은 다른 응답과 같이 밀리초까지만 (계약서 §7-2, v1.37.2). */
    @Test
    @DisplayName("기한·조치 시각이 밀리초까지만 나간다")
    void timestampsAreMilliseconds() throws Exception {
        String body = bodyOf(resolve("{\"action\":\"HIDE\"}").andExpect(status().isOk()));

        assertThat(fractionDigits(body, "hiddenUntil")).isLessThanOrEqualTo(3);
        assertThat(fractionDigits(body, "until")).isLessThanOrEqualTo(3);
        assertThat(fractionDigits(body, "resolvedAt")).isLessThanOrEqualTo(3);
    }

    /** 열람 자체가 기록 대상이다 (계약서 §7-2 — 접속기록). */
    @Test
    @DisplayName("상세를 열면 열람 기록이 남는다")
    void openingDetailIsAudited() throws Exception {
        Long reportId = reportRepository.findAll().get(0).getId();

        admin(get("/api/admin/reports/{id}", reportId)).andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("신고 열람");
                    assertThat(log.getTargetId()).isEqualTo(reportId);
                });
    }

    @Test
    @DisplayName("운영자 세션이 없으면 401, 운영자가 아니면 403 이다")
    void adminApiNeedsOperatorSession() throws Exception {
        mockMvc.perform(get("/api/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/admin/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("없는 신고는 404 다")
    void missingReportIs404() throws Exception {
        admin(get("/api/admin/reports/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    // ── 조치

    @Test
    @DisplayName("임시 가림은 글을 가리고 신고를 TEMP_HIDDEN 으로 남긴다")
    void hideHidesContentAndKeepsReportOpen() throws Exception {
        resolve("{\"action\":\"HIDE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TEMP_HIDDEN"))
                .andExpect(jsonPath("$.moderation.status").value("HIDDEN"))
                .andExpect(jsonPath("$.hiddenUntil").isNotEmpty())
                .andExpect(jsonPath("$.resolution.action").value("HIDE"));

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message")
                        .value("신고로 임시로 가려진 게시물입니다."));
    }

    /** 30일 안에 다시 정해야 하는 미완의 건이라 목록에 남아야 한다. */
    @Test
    @DisplayName("임시 가림한 건은 기본 목록에 그대로 보인다")
    void tempHiddenStaysInTheDefaultList() throws Exception {
        resolve("{\"action\":\"HIDE\"}").andExpect(status().isOk());

        admin(get("/api/admin/reports"))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("TEMP_HIDDEN"))
                .andExpect(jsonPath("$.content[0].hiddenUntil").isNotEmpty());
    }

    @Test
    @DisplayName("삭제는 글을 지우고 신고를 닫는다")
    void removeClosesTheReport() throws Exception {
        resolve("{\"action\":\"REMOVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.moderation.status").value("REMOVED"));

        mockMvc.perform(get("/api/posts/{id}", post.getId()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("운영 정책에 따라 삭제된 게시물입니다."));
    }

    @Test
    @DisplayName("조치 없음은 자동으로 가려진 글을 복원한다")
    void dismissRestoresAutoHiddenContent() throws Exception {
        autoHide();
        mockMvc.perform(get("/api/posts/{id}", post.getId())).andExpect(status().isGone());

        resolve("{\"action\":\"DISMISS\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.moderation").doesNotExist());

        mockMvc.perform(get("/api/posts/{id}", post.getId())).andExpect(status().isOk());
    }

    /** 같은 글에 대한 판단을 운영자가 여러 번 하지 않게 한다 (계약서 §7-2). */
    @Test
    @DisplayName("같은 대상의 다른 신고도 함께 닫힌다")
    void sameTargetReportsCloseTogether() throws Exception {
        User second = createUser("second@example.com", "두번째", "010-3333-3333");
        createTeam(second, "두번째 팀", "서울 강남구");
        report(second);
        assertThat(reportRepository.findAll()).hasSize(2);

        resolve("{\"action\":\"REMOVE\"}").andExpect(status().isOk());

        assertThat(reportRepository.findAll())
                .allSatisfy(saved -> assertThat(saved.getStatus())
                        .isEqualTo(ReportStatus.CLOSED));
    }

    @Test
    @DisplayName("이용 제한은 계정에만 걸리고 글은 그대로 둔다")
    void suspendOnlyLeavesContent() throws Exception {
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(userRepository.findById(author.getId()).orElseThrow()
                .isSuspendedAt(OffsetDateTime.now())).isTrue();
        mockMvc.perform(get("/api/posts/{id}", post.getId())).andExpect(status().isOk());
    }

    @Test
    @DisplayName("조치에 붙은 제한 날수가 상세에 남는다")
    void resolutionCarriesSuspendDays() throws Exception {
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":7}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution.suspendDays").value(7));

        Long reportId = reportRepository.findAll().get(0).getId();
        admin(get("/api/admin/reports/{id}", reportId))
                .andExpect(jsonPath("$.resolution.suspendDays").value(7));
    }

    /**
     * <b>운영자가 제한되면 스스로 풀 수 없다</b> (계약서 §7-2, v1.37.2). 이용 제한은
     * 운영자 API 까지 막으므로, 자기 팀이 신고된 건을 제한으로 처리하는 순간 신고함도
     * 해제 API 도 닫힌다 — 실제로 그렇게 잠겼다.
     */
    @Test
    @DisplayName("운영자 계정은 이용을 제한할 수 없다")
    void operatorCannotBeSuspended() throws Exception {
        reportRepository.deleteAll();
        Team operatorTeam = createTeam(operator, "운영자 팀", "서울 중구");
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"TEAM","targetId":%d,"reason":"NO_SHOW"}
                                """.formatted(operatorTeam.getId())))
                .andExpect(status().isCreated());

        // 400 이 아니라 409 다 (v1.37.3) — 요청 값이 틀린 것이 아니라 대상의 상태 때문에
        // 할 수 없는 일이고, FE 가 범위를 벗어난 기간(400)과 구별해야 안내가 갈린다.
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":7}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_NOT_SUSPENDABLE"));

        // 요청 값 검증이 먼저다 — 같은 잘못된 요청이 대상에 따라 다른 코드를 받으면 안 된다
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":99999}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'suspendDays')]").exists());

        // 콘텐츠 조치는 그대로 된다 — 막는 것은 제한뿐이다
        resolve("{\"action\":\"HIDE\"}").andExpect(status().isOk());
        assertThat(userRepository.findById(operator.getId()).orElseThrow()
                .isSuspendedAt(java.time.OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("조치하지 않으면서 계정만 제한할 수는 없다")
    void dismissCannotSuspend() throws Exception {
        resolve("{\"action\":\"DISMISS\",\"suspendDays\":7}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'suspendDays')]").exists());
    }

    @Test
    @DisplayName("이용 제한에는 기간이 필요하다")
    void suspendNeedsDays() throws Exception {
        resolve("{\"action\":\"SUSPEND_ONLY\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'suspendDays')]").exists());
    }

    /** 1:1 대화는 가리거나 지우는 대상이 아니다 — 차단과 이용 제한으로 다룬다. */
    @Test
    @DisplayName("채팅 메시지 신고에는 가림·삭제를 쓸 수 없다")
    void chatReportCannotBeHidden() throws Exception {
        reportRepository.deleteAll();
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,
                                 "reason":"ABUSE","chatDisclosureAgreed":true}
                                """))
                .andExpect(status().isCreated());

        resolve("{\"action\":\"HIDE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'action')]").exists());
    }

    // ── 통지 (계약서 §7-2 — 법무 초안 3-3절)

    @Test
    @DisplayName("조치하면 신고자와 게재자 양쪽에 고객센터 알림이 간다")
    void bothSidesAreNotified() throws Exception {
        resolve("{\"action\":\"HIDE\"}").andExpect(status().isOk());

        assertThat(noticesTo(reporter)).hasSize(1)
                .allSatisfy(content -> assertThat(content).contains("조치했습니다"));
        assertThat(noticesTo(author)).hasSize(1)
                .allSatisfy(content -> assertThat(content).contains("임시로 가려졌습니다"));
    }

    /**
     * 임시 가림 통지는 <b>"권리침해"라고 단정하지 않는다</b> (v1.38.5, 법무 대조).
     *
     * 스팸 신고로 가려진 글에도 "권리침해 신고로"가 나가고 있었다 — 사실과 다르고,
     * 받는 사람에게는 더 무거운 혐의로 읽힌다. 사유는 다음 줄의 "사유: …"가 말한다.
     */
    @Test
    @DisplayName("임시 가림 통지에 '권리침해' 가 들어가지 않는다")
    void hiddenNoticeDoesNotSayRightsInfringement() throws Exception {
        resolve("{\"action\":\"HIDE\"}").andExpect(status().isOk());

        assertThat(noticesTo(author)).singleElement(
                        org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("신고가 접수되어 임시로 가려졌습니다")
                .doesNotContain("권리침해");
    }

    /**
     * 이용 제한 통지에 <b>끝나는 날짜</b>가 들어간다 (v1.38.5).
     *
     * "7일 동안"만 적으면 언제부터 센 7일인지 받는 사람이 알 수 없다. 날짜는 그 계정에
     * 실제로 걸린 기한과 같은 값이어야 한다 — 통지와 실제가 어긋나면 이의 기한을
     * 잘못 계산하게 된다.
     */
    @Test
    @DisplayName("이용 제한 통지의 날짜가 실제 제한 기한과 같다")
    void suspensionNoticeCarriesTheEndDate() throws Exception {
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":7}").andExpect(status().isOk());

        String until = userRepository.findById(author.getId()).orElseThrow()
                .getSuspendedUntil()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy년 M월 d일"));
        assertThat(noticesTo(author))
                .filteredOn(content -> content.contains("이용이 제한됐습니다"))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains(until + "까지(7일 동안) 이용이 제한됐습니다")
                .as("무엇이 제한되는지는 적지 않는다 — 채팅 서버는 이용 제한을 보지 않는다")
                .doesNotContain("채팅");
    }

    /** 어느 통지에도 신고자가 누구인지 적지 않는다 (계약서 §7-2). */
    @Test
    @DisplayName("게재자에게 가는 통지에 신고자가 드러나지 않는다")
    void noticeNeverNamesTheReporter() throws Exception {
        resolve("{\"action\":\"REMOVE\"}").andExpect(status().isOk());

        assertThat(noticesTo(author))
                .allSatisfy(content -> assertThat(content)
                        .doesNotContain("김신고")
                        .doesNotContain("reporter@example.com"));
    }

    /** 이용자가 읽는 문장이다 — "모집글이(가)"처럼 나가면 안 된다. */
    @Test
    @DisplayName("통지 문구의 조사가 대상에 맞는다")
    void noticeUsesTheRightParticle() throws Exception {
        resolve("{\"action\":\"REMOVE\"}").andExpect(status().isOk());

        assertThat(noticesTo(author))
                .anySatisfy(content -> assertThat(content)
                        .contains("회원님의 모집글이 운영 정책 위반으로 삭제됐습니다")
                        .doesNotContain("이(가)"));
    }

    @Test
    @DisplayName("조치하지 않으면 신고자에게만 알린다")
    void dismissNotifiesOnlyTheReporter() throws Exception {
        resolve("{\"action\":\"DISMISS\"}").andExpect(status().isOk());

        assertThat(noticesTo(reporter)).hasSize(1)
                .allSatisfy(content -> assertThat(content).contains("차단 기능"));
        assertThat(noticesTo(author)).isEmpty();
    }

    /**
     * 가려졌다는 통지를 받은 사람에게 끝을 알리지 않으면 영문을 모른 채 남는다 —
     * 자동 가림이 풀릴 때는 게재자에게도 알린다 (계약서 §7-2).
     */
    @Test
    @DisplayName("자동으로 가려졌다가 복원되면 게재자에게도 알린다")
    void restoringAfterAutoHideNotifiesTheAuthor() throws Exception {
        autoHide();
        // 가려질 때 이미 한 번 알렸다 (아래 autoHideNotifiesTheAuthor)
        assertThat(noticesTo(author)).hasSize(1);

        resolve("{\"action\":\"DISMISS\"}").andExpect(status().isOk());

        assertThat(noticesTo(author)).hasSize(2)
                .last(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("다시 표시됩니다");
    }

    /**
     * <b>자동으로 가려질 때도 게재자에게 알린다</b> (계약서 §7-2). 알리지 않으면 글이
     * 왜 사라졌는지 알 길이 없고, 이의를 제기할 수도 없다 — 운영자 조치 전이라 더 그렇다.
     *
     * 신고자에게는 이때 알리지 않는다. 결과가 나온 것이 아니라 보류된 것이다.
     */
    @Test
    @DisplayName("자동 임시 가림이 걸리면 게재자에게 알린다")
    void autoHideNotifiesTheAuthor() throws Exception {
        autoHide();

        assertThat(noticesTo(author)).hasSize(1)
                .allSatisfy(content -> assertThat(content)
                        .contains("여러 건의 신고가 접수되어")
                        // 운영자 판단 전이라 "위반"이라고 단정하지 않는다
                        .doesNotContain("위반"));
        assertThat(noticesTo(reporter)).isEmpty();
    }

    /** 네 번째 신고가 기한을 30일 더 밀면, 신고만으로 글을 영영 가릴 수 있다. */
    @Test
    @DisplayName("이미 자동으로 가려진 뒤의 신고는 다시 알리지 않는다")
    void laterReportsDoNotRenotify() throws Exception {
        autoHide();
        User fourth = createUser("fourth@example.com", "네번째", "010-5555-5555");
        createTeam(fourth, "네번째 팀", "서울 마포구");

        report(fourth);

        assertThat(noticesTo(author)).hasSize(1);
    }

    @Test
    @DisplayName("이용 제한 통지에는 기간이 들어간다")
    void suspensionNoticeCarriesTheDays() throws Exception {
        resolve("{\"action\":\"SUSPEND_ONLY\",\"suspendDays\":7}").andExpect(status().isOk());

        assertThat(noticesTo(author))
                .anySatisfy(content -> assertThat(content).contains("7일 동안"));
    }

    /** 내부 메모는 운영자끼리 보는 값이다 — 통지에 실으면 그대로 사용자에게 간다. */
    @Test
    @DisplayName("내부 메모는 통지에 실리지 않는다")
    void noteStaysInternal() throws Exception {
        resolve("{\"action\":\"REMOVE\",\"note\":\"반복 신고자, 주의 관찰\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution.note").value("반복 신고자, 주의 관찰"));

        assertThat(noticesTo(author))
                .allSatisfy(content -> assertThat(content).doesNotContain("주의 관찰"));
    }

    // ── 헬퍼

    private void report(User user) throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                                """.formatted(post.getId())))
                .andExpect(status().isCreated());
    }

    /** 서로 다른 신고자 셋을 만들어 자동 임시 가림을 건다. */
    private void autoHide() throws Exception {
        User second = createUser("second@example.com", "두번째", "010-3333-3333");
        createTeam(second, "두번째 팀", "서울 강남구");
        User third = createUser("third@example.com", "세번째", "010-4444-4444");
        createTeam(third, "세번째 팀", "서울 노원구");
        report(second);
        report(third);
        assertThat(reportRepository.findAll())
                .allSatisfy(saved -> assertThat(saved.isAutoHidden()).isTrue());
    }

    private ResultActions resolve(String body) throws Exception {
        Report target = reportRepository.findAll().get(0);
        return admin(post("/api/admin/reports/{id}/resolve", target.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions admin(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        return mockMvc.perform(builder
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .header(OperatorGuard.HEADER, operatorToken(operator)));
    }

    /**
     * 응답에서 그 필드의 소수점 자릿수. 없으면 0.
     *
     * 윈도우는 100ns 정밀도라 그냥 두면 7자리가 나간다 — 엄격한 파서를 쓰는
     * 클라이언트가 걸리는 자리다.
     */
    private int fractionDigits(String body, String field) {
        Matcher matcher = Pattern.compile("\"" + field + "\":\"[^\"]*?(?:[.]([0-9]+))?[+Z]")
                .matcher(body);
        return matcher.find() && matcher.group(1) != null ? matcher.group(1).length() : 0;
    }

    /** 그 사람의 문의방에 쌓인 알림(NOTICE) 본문들. */
    private java.util.List<String> noticesTo(User user) {
        return supportMessageRepository.findAll().stream()
                .filter(message -> message.getUser().getId().equals(user.getId()))
                .filter(message -> message.getSender() == SupportSender.NOTICE)
                .map(message -> message.getContent())
                .toList();
    }
}
