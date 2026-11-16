package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * 시행일이 아직 오지 않았을 때 (계약서 §3 「동의」 판정, v1.35.3).
 *
 * <b>개정 약관은 시행 전에 공지한다.</b> 그래서 시행일 설정값을 미리 올려 두는 운영이
 * 자연스러운데, 그때 재동의를 요구하면 그 기간 동안 아무도 통과할 수 없다 — 다시 동의해도
 * 동의 시각이 그 날짜보다 이르니 풀리지 않는다. v1.34.1 의 판정이 그랬고, v1.35.3 이
 * "시행일이 이미 지났을 때만 본다"로 고쳤다.
 *
 * 설정값을 멀리 둔 것은 일부러다 — 테스트가 도는 날에 따라 답이 바뀌면 안 된다.
 */
@TestPropertySource(properties = "kickoff.legal.effective-at=2099-01-01T00:00:00+09:00")
class ConsentFutureEffectiveDateTest extends IntegrationTestSupport {

    /** 개정 전에 동의한 계정 — 설정값보다 한참 이르다. */
    private static final OffsetDateTime LONG_AGO =
            OffsetDateTime.parse("2026-08-01T10:00:00+09:00");

    @Test
    @DisplayName("시행일이 아직 오지 않았으면 그 전의 동의도 그대로 통과한다")
    void pastConsentPassesWhileDocumentIsNotYetInForce() throws Exception {
        User user = createUser("old@example.com", "김주장", "010-1111-1111");
        backdateConsents(user, LONG_AGO);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.pendingConsents").isEmpty());
        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
    }

    /**
     * <b>v1.34.1 에서는 여기가 막혔다.</b> 다시 동의해도 새 동의 시각이 미래의 시행일보다
     * 이르니 판정이 계속 "받아야 함"이었다 — 풀 방법이 없는 403 이다.
     */
    @Test
    @DisplayName("기록이 없어 동의를 받되, 제출하면 풀린다 — 막힌 채로 남지 않는다")
    void submittingClearsTheGateEvenBeforeTheEffectiveDate() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.pendingConsents",
                        org.hamcrest.Matchers.containsInAnyOrder("age14", "terms", "privacy")));

        mockMvc.perform(put("/api/users/me/consents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/me/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk());
        // 기록에는 그 사람이 본 문서의 시행일이 남는다 — 미래 날짜라도 그대로다
        assertThat(consentRepository.findByUserId(user.getId()))
                .isNotEmpty()
                .allSatisfy(consent -> assertThat(consent.getDocumentEffectiveAt().toInstant())
                        .isEqualTo(legalProperties.effectiveAt().toInstant()));
    }

    /** {@code ConsentRevisionTest} 와 같은 방법이다 — 엔티티에 setter 가 없다. */
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
