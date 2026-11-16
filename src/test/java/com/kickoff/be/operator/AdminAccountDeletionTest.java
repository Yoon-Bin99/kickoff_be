package com.kickoff.be.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.matchrequest.entity.MatchRequest;
import com.kickoff.be.matchrequest.entity.RequestStatus;
import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.post.entity.MatchPost;
import com.kickoff.be.post.entity.PostStatus;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 백업 복원 뒤의 재탈퇴 (계약서 §7-2 B-6, v1.37.3).
 *
 * <b>왜 API 로 만드는가.</b> 손으로 SQL 을 돌려 지우면 채팅 서버로 가는 {@code PURGE} 가
 * 빠지고, 탈퇴한 사람의 대화가 거기 남는다 — 방침이 약속한 "지체 없이 파기"가 깨진다.
 * 그래서 §3-4 의 탈퇴 경로를 그대로 다시 태운다.
 *
 * 이 테스트만 스위치를 켠다. 꺼져 있을 때의 404 는 {@code AdminDeletionDisabledTest} 가
 * 따로 본다 — 기본값이 꺼짐이라는 것이 이 기능의 핵심이라서다.
 */
@TestPropertySource(properties = "kickoff.admin.account-deletion.enabled=true")
class AdminAccountDeletionTest extends IntegrationTestSupport {

    private User operator;
    private User target;
    private Team targetTeam;

    @BeforeEach
    void setUpAccounts() {
        operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        target = createUser("target@example.com", "되살아난계정", "010-1111-1111");
        targetTeam = createTeam(target, "FC 새벽", "서울 강서구");
    }

    @Test
    @DisplayName("지운 id 와 이미 없던 id 를 나눠 돌려준다")
    void deletesAndReportsMissing() throws Exception {
        deletions("[%d, 999999]".formatted(target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", org.hamcrest.Matchers.contains(
                        target.getId().intValue())))
                .andExpect(jsonPath("$.notFound", org.hamcrest.Matchers.contains(999999)));

        assertThat(userRepository.findById(target.getId())).isEmpty();
        assertThat(teamRepository.findById(targetTeam.getId())).isEmpty();
    }

    /** 중간에 끊겨 같은 목록을 다시 돌리는 일이 정상 경로에 있다. */
    @Test
    @DisplayName("같은 목록을 다시 돌려도 안전하다 — 두 번째는 notFound 다")
    void runningTwiceIsSafe() throws Exception {
        deletions("[%d]".formatted(target.getId())).andExpect(status().isOk());

        deletions("[%d]".formatted(target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").isEmpty())
                .andExpect(jsonPath("$.notFound", org.hamcrest.Matchers.hasSize(1)));
    }

    /**
     * <b>비밀번호를 묻지 않는다.</b> 본인 확인 수단이라 운영자에게는 의미가 없다 —
     * 떠난 사람의 비밀번호를 운영자가 알 리도 없다.
     */
    @Test
    @DisplayName("비밀번호 없이 지운다")
    void noPasswordNeeded() throws Exception {
        deletions("[%d]".formatted(target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", org.hamcrest.Matchers.hasSize(1)));
    }

    /**
     * 예정된 매칭은 <b>막는 대신 취소한다</b> (계약서 §7-2 B-6). 이 계정은 이미 떠난
     * 사람이고(복원으로 되살아났을 뿐이다) 막아 봐야 아무도 그 경기를 치를 수 없다.
     */
    @Test
    @DisplayName("예정된 매칭이 있으면 §6-2 처럼 취소한 뒤 지운다")
    void upcomingMatchIsCancelledNotBlocking() throws Exception {
        User opponent = createUser("opponent@example.com", "상대", "010-2222-2222");
        Team opponentTeam = createTeam(opponent, "FC 노을", "서울 송파구");
        MatchPost opponentPost = createPost(opponentTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(7), true);
        MatchRequest request = acceptedRequest(opponentPost, targetTeam);

        deletions("[%d]".formatted(target.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", org.hamcrest.Matchers.hasSize(1)));

        // 상대 팀의 글은 다시 모집 중으로 돌아간다 — 그러지 않으면 매칭완료인 채
        // 상대가 사라진 글이 남는다
        assertThat(postRepository.findById(opponentPost.getId()).orElseThrow().getStatus())
                .isEqualTo(PostStatus.OPEN);
        // 신청 자체는 팀과 함께 사라진다
        assertThat(requestRepository.findById(request.getId())).isEmpty();
        assertThat(userRepository.findById(target.getId())).isEmpty();
    }

    @Test
    @DisplayName("상대 팀에게 매칭 취소 알림이 간다")
    void opponentIsNotified() throws Exception {
        User opponent = createUser("opponent@example.com", "상대", "010-2222-2222");
        Team opponentTeam = createTeam(opponent, "FC 노을", "서울 송파구");
        MatchPost post = createPost(opponentTeam, "다음 주 경기",
                OffsetDateTime.now().plusDays(7), true);
        acceptedRequest(post, targetTeam);
        // 토큰이 없으면 발송 자체를 건너뛴다 — 알림이 가는지를 보려면 등록해야 한다
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/me/push-token")
                        .header(HttpHeaders.AUTHORIZATION, bearer(opponent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expoPushToken\":\"ExponentPushToken[opponent]\"}"))
                .andExpect(status().isNoContent());
        pushClient.reset();

        deletions("[%d]".formatted(target.getId())).andExpect(status().isOk());

        assertThat(pushClient.sent())
                .as("상대는 경기 당일에야 알게 되면 안 된다")
                .isNotEmpty();
        assertThat(pushClient.last().data()).containsEntry("type", "MATCH_CANCELED");
    }

    /** 대화가 거기 남지 않게 하는 것이 이 API 를 만든 이유다. */
    @Test
    @DisplayName("채팅 서버에 PURGE 가 쌓인다 — 손으로 지울 때 빠지는 자리다")
    void chatPurgeIsQueued() throws Exception {
        deletions("[%d]".formatted(target.getId())).andExpect(status().isOk());

        assertThat(chatOutboxRepository.findAll())
                .anySatisfy(event -> assertThat(event.getEventType().name()).isEqualTo("PURGE"));
    }

    // ── 거절

    @Test
    @DisplayName("자기 계정은 여기서 지울 수 없다")
    void operatorCannotDeleteSelf() throws Exception {
        deletions("[%d, %d]".formatted(target.getId(), operator.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'userIds')]").exists());

        assertThat(userRepository.findById(target.getId()))
                .as("한 명도 지우지 않는다").isPresent();
    }

    @Test
    @DisplayName("빈 목록과 200개 초과는 400 이다")
    void listSizeIsBounded() throws Exception {
        deletions("[]").andExpect(status().isBadRequest());

        String tooMany = java.util.stream.IntStream.rangeClosed(1, 201)
                .mapToObj(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        deletions("[" + tooMany + "]").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("운영자 세션이 없으면 401, 운영자가 아니면 403 이다")
    void needsOperatorSession() throws Exception {
        mockMvc.perform(post("/api/admin/users/deletions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[%d]}".formatted(target.getId())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_SESSION_REQUIRED"));

        mockMvc.perform(post("/api/admin/users/deletions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(target))
                        .header(OperatorGuard.HEADER, operatorToken(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[999999]}"))
                .andExpect(status().isForbidden());
    }

    /** 접속기록이 "누구의 정보를" 한 명씩 가리켜야 한다 (계약서 §7-2). */
    @Test
    @DisplayName("대상 userId 가 전부 접속기록에 남는다")
    void everyTargetIsAudited() throws Exception {
        User second = createUser("second@example.com", "둘째", "010-3333-3333");

        deletions("[%d, %d]".formatted(target.getId(), second.getId()))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .filteredOn(log -> "계정 재탈퇴".equals(log.getAction()))
                .extracting(log -> log.getTargetUserId())
                .contains(target.getId(), second.getId());
    }

    private ResultActions deletions(String userIdsJson) throws Exception {
        return mockMvc.perform(post("/api/admin/users/deletions")
                .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                .header(OperatorGuard.HEADER, operatorToken(operator))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":%s}".formatted(userIdsJson)));
    }
}
