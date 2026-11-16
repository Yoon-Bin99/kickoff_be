package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 이용 제한 (계약서 §7-2 「이용 제한」, v1.35.0).
 *
 * <b>이의 제기 창구를 막지 않는 것이 이 기능의 전제다.</b> 제한된 사람이 고객센터에도
 * 못 가고 탈퇴도 못 하면, 제한이 통보 없는 추방이 된다 — 조치가 부당해도 말할 곳이 없다.
 *
 * 검사 순서는 인증(401) → 동의(403 {@code CONSENT_REQUIRED}) → 이용 제한(403
 * {@code ACCOUNT_SUSPENDED}) 이다. 두 403 을 코드로 가르지 않으면 FE 가 제한된 계정을
 * 동의 화면으로 보낸다.
 */
class SuspensionTest extends IntegrationTestSupport {

    private User suspended;

    @BeforeEach
    void setUpSuspended() {
        suspended = createUser("suspended@example.com", "제한계정", "010-1111-1111");
        suspend(suspended, OffsetDateTime.now().plusDays(7));
    }

    @Test
    @DisplayName("제한 중인 계정은 인증 API 에서 403 ACCOUNT_SUSPENDED 다")
    void suspendedAccountIsBlocked() throws Exception {
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    /** 로그인 직후 바로 안내할 수 있어야 한다 — 403 을 받기 전에도 알 수 있게. */
    @Test
    @DisplayName("GET /api/auth/me 에 suspendedUntil 이 실린다")
    void meCarriesSuspendedUntil() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspendedUntil").isNotEmpty());
    }

    @Test
    @DisplayName("제한이 없으면 suspendedUntil 은 null 이다")
    void normalAccountHasNoSuspension() throws Exception {
        User normal = createUser("normal@example.com", "보통계정", "010-2222-2222");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(normal)))
                .andExpect(jsonPath("$.suspendedUntil").doesNotExist());
    }

    // ── 열어 두는 길 (계약서 §7-2)

    @Test
    @DisplayName("고객센터는 제한 중에도 열려 있다 — 이의 제기 창구다")
    void supportStaysOpen() throws Exception {
        mockMvc.perform(get("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/support/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"제한이 부당합니다\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("탈퇴와 내 정보 조회는 제한 중에도 된다")
    void leavingStaysOpen() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk());

        // 이메일 계정이라 비밀번호 확인이 함께 온다 (§3-4) — 제한과는 무관한 규칙이다.
        mockMvc.perform(delete("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
    }

    /**
     * 운영자 쪽 문의함은 열지 않는다 — 그쪽은 §7-2 의 운영자 검사가 따로 본다.
     * 사용자 쪽 경로만 완전 일치로 열어 둔 이유다.
     */
    @Test
    @DisplayName("운영자 쪽 문의함은 제한 예외가 아니다")
    void operatorInboxIsNotExempt() throws Exception {
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    // ── 순서 (계약서 §7-2)

    /**
     * 동의도 안 한 제한 계정이 "제한됐다"는 안내를 먼저 받으면, 동의 화면을 지나기 전에는
     * 아무것도 할 수 없다는 사실이 가려진다.
     */
    @Test
    @DisplayName("동의가 먼저다 — 동의도 안 했으면 CONSENT_REQUIRED 다")
    void consentIsCheckedFirst() throws Exception {
        User pending = createUserWithoutConsents("동의안함", "010-3333-3333");
        suspend(pending, OffsetDateTime.now().plusDays(7));

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("토큰이 없으면 403 이 아니라 401 이다")
    void unauthenticatedStillGets401() throws Exception {
        mockMvc.perform(get("/api/users/me/teams"))
                .andExpect(status().isUnauthorized());
    }

    // ── 풀리는 길

    /** 해제 배치를 두지 않는다 — 배치가 도는 시각까지 늦게 풀리고, 안 도는 날이 있다. */
    @Test
    @DisplayName("기한이 지나면 저절로 풀린다")
    void expiredSuspensionIsOver() throws Exception {
        suspend(suspended, OffsetDateTime.now().minusMinutes(1));

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("운영자가 해제하면 바로 풀린다")
    void operatorCanClearSuspension() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");

        mockMvc.perform(delete("/api/admin/users/{id}/suspension", suspended.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, operatorToken(operator)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(suspended)))
                .andExpect(status().isOk());
        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("이용 제한 해제");
                    assertThat(log.getTargetUserId()).isEqualTo(suspended.getId());
                });
    }

    @Test
    @DisplayName("해제는 운영자만 할 수 있고 없는 사용자는 404 다")
    void clearingNeedsOperator() throws Exception {
        User operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");

        mockMvc.perform(delete("/api/admin/users/{id}/suspension", suspended.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/admin/users/{id}/suspension", 999999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, operatorToken(operator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    /** 동의 화면은 제한과 무관하게 지날 수 있어야 한다 — 두 게이트의 예외가 겹치는 자리다. */
    @Test
    @DisplayName("동의 제출은 제한 중에도 된다")
    void consentSubmitStaysOpen() throws Exception {
        User pending = createUserWithoutConsents("동의안함", "010-3333-3333");
        suspend(pending, OffsetDateTime.now().plusDays(7));

        mockMvc.perform(put("/api/users/me/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pending))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isNoContent());
    }

    private void suspend(User user, OffsetDateTime until) {
        transactionTemplate.executeWithoutResult(status -> userRepository
                .findById(user.getId()).orElseThrow().suspendUntil(until));
    }
}
