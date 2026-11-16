package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 문서를 개정하면 다시 받는다 (계약서 §3 「동의」 — {@code pendingConsents} 판정).
 *
 * <b>판정이 서버로 온 이유가 이것이다.</b> 전에는 FE 가 상수({@code TERMS_EFFECTIVE_AT})로
 * 시행일을 들고 비교했다 — 문서를 개정하는 날 앱이 업데이트되지 않으면 아무도 다시 동의하지
 * 않았고, 그 사실이 어디에도 드러나지 않았다.
 *
 * 설정값은 건드리지 않고 <b>기록을 과거로 돌린다</b> — 구조 개편 전에 동의한 계정이 정확히
 * 이 모양이고, 시행일({@code 2026-09-08})을 실제 설정대로 두고 보는 쪽이 낫다.
 */
class ConsentRevisionTest extends IntegrationTestSupport {

    /** 현행 시행일보다 이른 동의 — 개정 전에 동의한 계정. */
    private static final OffsetDateTime BEFORE_EFFECTIVE =
            OffsetDateTime.parse("2026-08-01T10:00:00+09:00");

    /** 현행 시행일 뒤의 동의 — 이미 현행 문서에 동의한 계정. */
    private static final OffsetDateTime AFTER_EFFECTIVE =
            OffsetDateTime.parse("2026-10-01T10:00:00+09:00");

    @Test
    @DisplayName("시행일보다 이른 동의는 다시 받는다")
    void consentOlderThanEffectiveDateIsAskedAgain() throws Exception {
        User user = createUser("old@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, BEFORE_EFFECTIVE);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.pendingConsents",
                        org.hamcrest.Matchers.containsInAnyOrder("age14", "terms", "privacy")));
    }

    /**
     * 기록이 <b>있는데도</b> 막힌다 — 이 개편 전에는 기록이 있으면 통과였다. 개정된 문서가
     * 서빙되는데 아무도 다시 동의하지 않는 상태가 바로 그 구멍이었다.
     */
    @Test
    @DisplayName("다시 받아야 하는 계정은 인증 API 에서 403 이다")
    void staleConsentBlocksAuthenticatedApi() throws Exception {
        User user = createUser("old@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, BEFORE_EFFECTIVE);

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }

    @Test
    @DisplayName("다시 동의하면 통과하고 기록의 시행일이 현행으로 바뀐다")
    void reconsentingClearsTheGate() throws Exception {
        User user = createUser("old@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, BEFORE_EFFECTIVE);

        mockMvc.perform(put("/api/users/me/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
        // 행이 쌓이지 않고 갱신된다 (V27 의 unique) — 판정에는 가장 최근 한 건만 쓰인다
        assertThat(consentRepository.findByUserId(user.getId())).hasSize(3);
        assertThat(consentRepository
                .findByUserIdAndConsentType(user.getId(), ConsentType.TERMS)
                .orElseThrow().getDocumentEffectiveAt().toInstant())
                .as("어느 문서에 동의한 기록인지가 현행으로 바뀌어야 한다")
                .isEqualTo(legalProperties.effectiveAt().toInstant());
    }

    /**
     * 시행일 <b>뒤에</b> 동의한 기록은 그대로 둔다. 여기서 또 물으면 개정과 무관한 사용자가
     * 로그인마다 동의 화면을 본다.
     */
    @Test
    @DisplayName("시행일 뒤의 동의는 다시 받지 않는다")
    void consentAfterEffectiveDateIsKept() throws Exception {
        User user = createUser("ok@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, AFTER_EFFECTIVE);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.pendingConsents").isEmpty());
    }

    /**
     * 팀을 가진 계정은 연락처 제공 동의까지 다시 받는다 — 제3자 제공 동의 문구도 방침의
     * 일부라서(제17조) 개정되면 같은 기준으로 본다.
     */
    @Test
    @DisplayName("감독은 연락처 제공 동의까지 다시 받는다")
    void teamOwnerIsAskedForContactSharingToo() throws Exception {
        User owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        createTeam(owner, "FC 새벽", "서울 강서구");
        backdateConsents(owner, BEFORE_EFFECTIVE);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.pendingConsents",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                "age14", "terms", "privacy", "contactSharing")));
    }

    /**
     * 가입 화면 세 항목을 마친 시각({@code termsAgreedAt}, 표시용)은 기록에서 계산한다 —
     * {@code users.terms_agreed_at} 은 V27 에서 지웠다. 셋 중 가장 늦은 시각이다.
     */
    @Test
    @DisplayName("termsAgreedAt 은 기록의 가장 늦은 시각이다")
    void termsAgreedAtComesFromRecords() throws Exception {
        User user = createUser("ok@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, AFTER_EFFECTIVE);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.termsAgreedAt").isNotEmpty());
        assertThat(consentService.signupConsentCompletedAt(user).toInstant())
                .isEqualTo(AFTER_EFFECTIVE.toInstant());
    }

    /**
     * 기록을 과거로 돌린다. 엔티티에 setter 가 없으므로(작업 규칙) 네이티브로 쓴다 —
     * 테스트가 과거에 동의한 계정을 만드는 유일한 방법이다.
     */
    private void backdateConsents(User user, OffsetDateTime agreedAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createNativeQuery("update user_consents set agreed_at = :agreedAt "
                        + "where user_id = :userId")
                .setParameter("agreedAt", agreedAt)
                .setParameter("userId", user.getId())
                .executeUpdate());
        entityManager.clear();
    }
}
