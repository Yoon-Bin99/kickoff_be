package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.operator.entity.OperatorAuditLog;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 신고·차단·가림이 걸린 계정의 탈퇴 (계약서 §3-4·§7-2).
 *
 * <b>탈퇴를 못 하게 만드는 버그는 무겁다.</b> 차단당한 팀의 감독이 탈퇴하면 500 이 나던
 * 적이 있다 — {@code team_blocks} 가 팀을 외래키로 참조하는데 팀을 먼저 지웠다. 이용자가
 * 떠날 수 없는 상태였고, 그건 기능 문제가 아니라 약속을 어기는 일이다(방침 §3-1).
 *
 * v1.35.0 이 표를 여섯 개 더했다(신고·대화 사본·차단·접속기록·동의·가림 칸). 감독이
 * 탈퇴하면 팀과 그 팀의 글·리뷰가 함께 사라지므로, <b>그 표들이 가리키는 모든 방향</b>을
 * 여기서 한 번에 밟는다.
 */
class AccountDeletionWithModerationTest extends IntegrationTestSupport {

    private User owner;
    private User other;
    private Team ownerTeam;
    private Team otherTeam;

    @BeforeEach
    void setUpTeams() {
        owner = createUser("owner@example.com", "김감독", "010-1111-1111");
        other = createUser("other@example.com", "박감독", "010-2222-2222");
        ownerTeam = createTeam(owner, "FC 새벽", "서울 강서구");
        otherTeam = createTeam(other, "FC 노을", "서울 송파구");
    }

    // ── 차단 (team_blocks — 사용자와 팀 양쪽을 참조한다)

    /** 이 경로가 500 이던 자리다. 차단은 상대가 건 것이라 떠나는 사람이 풀 수도 없었다. */
    @Test
    @DisplayName("차단당한 팀의 감독도 탈퇴할 수 있다")
    void blockedTeamOwnerCanLeave() throws Exception {
        block(other, ownerTeam);

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(blockRepository.findAll()).isEmpty();
        assertThat(teamRepository.findById(ownerTeam.getId())).isEmpty();
    }

    @Test
    @DisplayName("차단한 쪽이 탈퇴하면 그 차단이 사라진다")
    void blockerCanLeave() throws Exception {
        block(owner, otherTeam);

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(blockRepository.findAll()).isEmpty();
        // 상대 팀은 그대로다 — 차단은 그 사람의 화면 설정이었을 뿐이다
        assertThat(teamRepository.findById(otherTeam.getId())).isPresent();
    }

    /** 팀원이 건 차단도 그 팀이 사라지면 함께 사라진다 — 가리킬 대상이 없다. */
    @Test
    @DisplayName("팀 없는 계정이 건 차단도 대상 팀과 함께 사라진다")
    void blockByTeamlessUserGoesWithTheTeam() throws Exception {
        User watcher = createUser("watcher@example.com", "구경꾼", "010-7777-7777");
        block(watcher, ownerTeam);

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(blockRepository.findAll()).isEmpty();
        assertThat(userRepository.findById(watcher.getId()))
                .as("차단을 건 사람은 그대로 남는다").isPresent();
    }

    // ── 신고 (reports — 외래키가 없고 식별값만 들고 있다)

    @Test
    @DisplayName("신고된 글이 있는 팀의 감독도 탈퇴할 수 있다")
    void ownerOfReportedPostCanLeave() throws Exception {
        MatchPost post = createPost(ownerTeam, "신고당한 글");
        report(other, "POST", post.getId(), "SPAM");

        withdraw(owner).andExpect(status().isNoContent());

        // 글은 사라지고 신고 건은 남는다 — 대상 종류·사유·조치는 보관 기간까지 남는다
        assertThat(postRepository.findById(post.getId())).isEmpty();
        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getReportedUserId()).as("신고된 쪽 식별값은 비워진다").isNull();
            assertThat(saved.getTargetTitle()).as("사본은 남는다").isNotBlank();
        });
    }

    @Test
    @DisplayName("신고한 사람이 탈퇴하면 신고자 식별값만 비워진다")
    void reporterCanLeave() throws Exception {
        MatchPost post = createPost(otherTeam, "남의 글");
        report(owner, "POST", post.getId(), "SPAM");

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(reportRepository.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getReporterUserId()).isNull();
            assertThat(saved.getReporterTeamId()).isNull();
            assertThat(saved.getReportedUserId()).isEqualTo(other.getId());
        });
    }

    @Test
    @DisplayName("다른 사람이 신고한 팀의 감독도 탈퇴할 수 있다")
    void ownerOfReportedTeamCanLeave() throws Exception {
        report(other, "TEAM", ownerTeam.getId(), "ABUSE");

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(teamRepository.findById(ownerTeam.getId())).isEmpty();
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    /** 가려진 리뷰는 팀과 함께 사라진다 — 가림 칸은 리뷰 행에 붙어 있다. */
    @Test
    @DisplayName("가려진 리뷰를 쓴 팀의 감독도 탈퇴할 수 있다")
    void ownerOfHiddenReviewCanLeave() throws Exception {
        Review review = reviewFromOwnerTeam();
        transactionTemplate.executeWithoutResult(status -> reviewRepository
                .findById(review.getId()).orElseThrow().hide(OffsetDateTime.now()));
        report(other, "REVIEW", review.getId(), "ABUSE");

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(reviewRepository.findById(review.getId())).isEmpty();
        assertThat(reportRepository.findAll()).hasSize(1);
    }

    /** 가려진 글도 마찬가지다 — 조치 중이라고 탈퇴가 막히지는 않는다. */
    @Test
    @DisplayName("가려진 글이 있는 팀의 감독도 탈퇴할 수 있다")
    void ownerOfHiddenPostCanLeave() throws Exception {
        MatchPost post = createPost(ownerTeam, "가려진 글");
        transactionTemplate.executeWithoutResult(status -> postRepository
                .findById(post.getId()).orElseThrow().hide(OffsetDateTime.now()));

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(postRepository.findById(post.getId())).isEmpty();
    }

    // ── 접속기록 (operator_audit_logs — 대상 userId 를 들고 있지만 외래키가 없다)

    /**
     * <b>접속기록은 탈퇴해도 남는다</b> (계약서 §7-2 — 법령상 1년 보관). 다른 기록과
     * 반대 방향이라 처리방침에 그렇게 적는다. 외래키로 묶지 않은 이유가 이것이다.
     */
    @Test
    @DisplayName("접속기록에 대상으로 남은 계정도 탈퇴할 수 있고, 기록은 남는다")
    void auditedUserCanLeave() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        operatorGuard.record(operator, "문의방 열람", owner.getId(), null);
        assertThat(auditLogRepository.findAll()).hasSize(1);

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(auditLogRepository.findAll())
                .singleElement()
                .extracting(OperatorAuditLog::getTargetUserId)
                .isEqualTo(owner.getId());
    }

    // ── 한꺼번에

    /** 실제 사고는 여러 개가 겹쳤을 때 난다 — 하나씩 통과해도 순서가 틀리면 여기서 깨진다. */
    @Test
    @DisplayName("신고·차단·가림·접속기록이 전부 걸린 계정도 탈퇴한다")
    void everythingAtOnce() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        MatchPost post = createPost(ownerTeam, "신고당한 글");
        report(other, "POST", post.getId(), "SPAM");
        report(owner, "TEAM", otherTeam.getId(), "ABUSE");
        block(other, ownerTeam);
        block(owner, otherTeam);
        transactionTemplate.executeWithoutResult(status -> postRepository
                .findById(post.getId()).orElseThrow().hide(OffsetDateTime.now()));
        operatorGuard.record(operator, "신고 열람", owner.getId(), 1L);

        withdraw(owner).andExpect(status().isNoContent());

        assertThat(userRepository.findById(owner.getId())).isEmpty();
        assertThat(teamRepository.findById(ownerTeam.getId())).isEmpty();
        assertThat(blockRepository.findAll()).isEmpty();
        assertThat(reportRepository.findAll()).hasSize(2);
        assertThat(auditLogRepository.findAll()).hasSize(1);
    }

    // ── 헬퍼

    private ResultActions withdraw(User user) throws Exception {
        return mockMvc.perform(delete("/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + PASSWORD + "\"}"));
    }

    private void block(User blocker, Team target) throws Exception {
        mockMvc.perform(post("/api/blocks")
                        .header(HttpHeaders.AUTHORIZATION, bearer(blocker))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":%d}".formatted(target.getId())))
                .andExpect(status().isCreated());
    }

    private void report(User reporter, String targetType, Long targetId, String reason)
            throws Exception {
        mockMvc.perform(post("/api/reports")
                        .header(HttpHeaders.AUTHORIZATION, bearer(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetType":"%s","targetId":%d,"reason":"%s"}
                                """.formatted(targetType, targetId, reason)))
                .andExpect(status().isCreated());
    }

    /** 내 팀이 상대 팀에게 남긴 리뷰 한 건 — 지난 경기의 수락된 매칭이 필요하다. */
    private Review reviewFromOwnerTeam() throws Exception {
        MatchPost past = createPost(otherTeam, "지난 경기",
                OffsetDateTime.now().minusDays(3), true);
        MatchRequest request = acceptedRequest(past, ownerTeam);
        mockMvc.perform(post("/api/requests/{id}/review", request.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"좋았습니다.\"}"))
                .andExpect(status().isCreated());
        return reviewRepository.findAll().get(0);
    }
}
