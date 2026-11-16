package com.kickoff.be.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.report.entity.Report;
import com.kickoff.be.report.service.ReportRetentionJob;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 신고 기록의 보관과 파기 (계약서 §7-2 「기록의 보관」, v1.35.0).
 *
 * <b>기간이 둘인 이유.</b> 조치가 있었던 신고는 분쟁의 근거가 될 수 있어 1년, 조치 없이
 * 끝난 신고는 90일이다 — 아무 일도 일어나지 않은 신고를 1년 들고 있을 근거가 없다
 * (개인정보 보호법 제21조 — 목적을 달성하면 지체 없이 파기).
 */
class ReportRetentionTest extends IntegrationTestSupport {

    @Autowired
    private ReportRetentionJob retentionJob;

    private User reporter;
    private User author;
    private MatchPost post;

    @BeforeEach
    void setUpReport() throws Exception {
        reporter = createUser("reporter@example.com", "김신고", "010-1111-1111");
        author = createUser("author@example.com", "박작성", "010-2222-2222");
        createTeam(reporter, "FC 새벽", "서울 강서구");
        Team authorTeam = createTeam(author, "FC 노을", "서울 송파구");
        post = createPost(authorTeam, "토요일 아침 상대 구합니다");
        report();
    }

    /** 운영자가 아직 보지 않은 건이 사라지면 신고가 조용히 없어진다. */
    @Test
    @DisplayName("처리 전 신고는 아무리 오래돼도 지우지 않는다")
    void pendingReportsAreKept() {
        backdate(OffsetDateTime.now().minusDays(400), null, null);

        retentionJob.purgeExpiredReports();

        assertThat(reportRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("조치 없이 끝난 신고는 90일 뒤에 지운다")
    void dismissedReportsGoAfter90Days() {
        backdate(OffsetDateTime.now().minusDays(100),
                OffsetDateTime.now().minusDays(91), "DISMISS");

        retentionJob.purgeExpiredReports();

        assertThat(reportRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("조치가 있었던 신고는 90일로는 지우지 않는다 — 1년이다")
    void resolvedReportsSurvive90Days() {
        backdate(OffsetDateTime.now().minusDays(100),
                OffsetDateTime.now().minusDays(91), "REMOVE");

        retentionJob.purgeExpiredReports();

        assertThat(reportRepository.findAll()).hasSize(1);

        backdate(OffsetDateTime.now().minusDays(400),
                OffsetDateTime.now().minusDays(366), "REMOVE");
        retentionJob.purgeExpiredReports();
        assertThat(reportRepository.findAll()).isEmpty();
    }

    /** 사본도 함께 지운다 — 신고가 사라졌는데 대화만 남으면 보관할 근거가 없다. */
    @Test
    @DisplayName("신고를 지우면 채팅 문맥 사본도 함께 사라진다")
    void chatCopiesGoWithTheReport() throws Exception {
        reportRepository.deleteAll();
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,
                                 "reason":"ABUSE","chatDisclosureAgreed":true}
                                """))
                .andExpect(status().isCreated());
        assertThat(reportChatMessageRepository.findAll()).isNotEmpty();
        backdate(OffsetDateTime.now().minusDays(100),
                OffsetDateTime.now().minusDays(91), "DISMISS");

        retentionJob.purgeExpiredReports();

        assertThat(reportRepository.findAll()).isEmpty();
        assertThat(reportChatMessageRepository.findAll()).isEmpty();
    }

    // ── 탈퇴 (계약서 §7-2 — 식별값을 비우고 대화 사본은 지운다)

    @Test
    @DisplayName("신고자가 탈퇴하면 건은 남고 신고자만 비워진다")
    void withdrawingReporterIsForgotten() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getReporterUserId()).isNull();
            assertThat(saved.getReporterTeamId()).isNull();
            // 대상 종류·사유·조치는 보관 기간까지 남는다
            assertThat(saved.getReason()).isNotNull();
            assertThat(saved.getTargetType()).isNotNull();
        });
    }

    @Test
    @DisplayName("신고된 쪽이 탈퇴하면 그쪽 식별값만 비워진다")
    void withdrawingReportedIsForgotten() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getReportedUserId()).isNull();
            assertThat(saved.getReporterUserId()).isEqualTo(reporter.getId());
        });
    }

    /** "대화는 탈퇴와 함께 지운다"(§3-4)는 약속이 사본에도 그대로 적용된다. */
    @Test
    @DisplayName("탈퇴하면 채팅 문맥 사본은 지워진다")
    void chatCopiesAreDeletedOnWithdrawal() throws Exception {
        reportRepository.deleteAll();
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"CHAT_MESSAGE","targetId":45,"requestId":7,
                                 "reason":"ABUSE","chatDisclosureAgreed":true}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(reportChatMessageRepository.findAll()).isEmpty();
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    // ── 헬퍼

    private void report() throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"POST","targetId":%d,"reason":"SPAM"}
                                """.formatted(post.getId())))
                .andExpect(status().isCreated());
    }

    /**
     * 신고를 과거로 돌린다. 엔티티에 setter 가 없고({@code @Setter} 금지) 생성 시각은
     * 감사 필드라, 네이티브로 쓰는 것이 테스트가 오래된 기록을 만드는 유일한 방법이다.
     */
    private void backdate(OffsetDateTime createdAt, OffsetDateTime resolvedAt, String action) {
        Long id = reportRepository.findAll().get(0).getId();
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("update reports set created_at = :createdAt, "
                        + "resolved_at = :resolvedAt, resolution_action = :action, "
                        + "status = :status where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("resolvedAt", resolvedAt)
                .setParameter("action", action)
                .setParameter("status", resolvedAt == null ? "OPEN" : "CLOSED")
                .setParameter("id", id)
                .executeUpdate());
        entityManager.clear();
        assertThat(reportRepository.findById(id)).isPresent();
        Report backdated = reportRepository.findById(id).orElseThrow();
        assertThat(backdated.getCreatedAt()).isNotNull();
    }
}
