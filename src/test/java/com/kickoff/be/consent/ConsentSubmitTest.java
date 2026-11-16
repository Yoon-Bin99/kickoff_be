package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 동의 화면이 보내는 것 (계약서 §3 — {@code PUT /api/users/me/consents}, v1.34.0).
 *
 * 이 한 API 가 소셜 최초 로그인과 개정 후 재동의를 모두 받는다. 무엇이 필수인지는
 * <b>서버가 정한다</b>({@code pendingConsents}) — 요청이 정하게 두면 FE 가 항목을 빠뜨린
 * 채로 통과할 수 있다.
 */
class ConsentSubmitTest extends IntegrationTestSupport {

    @Test
    @DisplayName("받아야 하는 항목을 모두 보내면 204 다")
    void submittingAllPendingItemsSucceeds() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        submit(user, "{\"age14\":true,\"terms\":true,\"privacy\":true}")
                .andExpect(status().isNoContent());

        assertThat(consentRepository.findByUserId(user.getId())).hasSize(3);
    }

    /**
     * <b>{@code field} 가 항목 키여야 한다.</b> FE 동의 화면은 체크박스가 여럿이고, 어느
     * 칸을 가리켜야 할지 모르면 사용자는 무엇이 빠졌는지 스스로 찾아야 한다.
     */
    @Test
    @DisplayName("빠진 항목이 field 로 온다")
    void missingItemIsNamed() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        submit(user, "{\"age14\":true,\"terms\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'privacy')]").exists());
    }

    @Test
    @DisplayName("false 로 보낸 항목도 거부로 보고 400 이다")
    void refusedItemIsRejected() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        submit(user, "{\"age14\":true,\"terms\":false,\"privacy\":true}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'terms')]").exists());
        assertThat(consentRepository.findByUserId(user.getId()))
                .as("하나라도 거부하면 아무것도 기록하지 않는다").isEmpty();
    }

    /** 동의 화면에서 네트워크가 끊겨 재시도하는 경우가 정상 경로에 있다. */
    @Test
    @DisplayName("멱등이다 — 두 번 보내도 204 이고 시각만 갱신된다")
    void submittingTwiceIsIdempotent() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");
        submit(user, "{\"age14\":true,\"terms\":true,\"privacy\":true}")
                .andExpect(status().isNoContent());
        OffsetDateTime first = consentRepository
                .findByUserIdAndConsentType(user.getId(), ConsentType.TERMS)
                .orElseThrow().getAgreedAt();

        submit(user, "{\"age14\":true,\"terms\":true,\"privacy\":true}")
                .andExpect(status().isNoContent());

        assertThat(consentRepository.findByUserId(user.getId())).hasSize(3);
        assertThat(consentRepository
                .findByUserIdAndConsentType(user.getId(), ConsentType.TERMS)
                .orElseThrow().getAgreedAt()).isAfterOrEqualTo(first);
    }

    /**
     * 받을 항목이 아닌 키를 {@code true} 로 보내면 함께 기록한다 — 팀을 만들기 전에 미리
     * 체크한 사용자를 거부할 이유가 없다.
     */
    @Test
    @DisplayName("받을 항목이 아닌 키도 true 면 함께 기록한다")
    void extraTrueItemIsRecorded() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        submit(user, """
                {"age14":true,"terms":true,"privacy":true,"contactSharing":true}
                """).andExpect(status().isNoContent());

        assertThat(consentRepository
                .findByUserIdAndConsentType(user.getId(), ConsentType.CONTACT_SHARING))
                .isPresent();
    }

    /** {@code false} 는 거부다 — 받을 항목이 아니어도 조용히 무시하지 않는다. */
    @Test
    @DisplayName("받을 항목이 아닌 키를 false 로 보내면 400 이다")
    void extraFalseItemIsRejected() throws Exception {
        User user = createUserWithoutConsents("카카오가입자", "010-9999-9999");

        submit(user, """
                {"age14":true,"terms":true,"privacy":true,"contactSharing":false}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'contactSharing')]").exists());
    }

    @Test
    @DisplayName("토큰이 없으면 401 이다")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/users/me/consents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"age14\":true,\"terms\":true,\"privacy\":true}"))
                .andExpect(status().isUnauthorized());
    }

    /** 옛 경로는 폐지했다 (계약서 §3 — 404). */
    @Test
    @DisplayName("옛 POST /me/terms-agreement 는 404 다")
    void legacyEndpointIsGone() throws Exception {
        User user = createUser("ok@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(post("/api/users/me/terms-agreement")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNotFound());
    }

    // ── 감독에게만 받는 항목 (계약서 §3, v1.34.1)

    /**
     * 팀이 없으면 받을 항목이 아니다 — 전화번호가 상대에게 넘어갈 일이 없다. 여기서
     * 필수로 요구하면 <b>팀원으로만 활동할 가입자</b>에게 제3자 제공 동의를 강요하게 된다.
     */
    @Test
    @DisplayName("팀이 없으면 contactSharing 은 받을 항목이 아니다")
    void contactSharingIsNotAskedWithoutTeam() throws Exception {
        User user = createUser("ok@example.com", "김주장", "010-1111-1111");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.pendingConsents").isEmpty());
    }

    /**
     * 팀을 가진 계정에 기록이 없으면 그 항목이 뜬다. 구조 개편 전에 팀을 만든 계정이
     * 정확히 이 상태다.
     */
    @Test
    @DisplayName("팀이 있는데 기록이 없으면 contactSharing 이 pendingConsents 에 온다")
    void teamOwnerWithoutRecordIsAsked() throws Exception {
        User owner = createUser("owner@example.com", "김주장", "010-1111-1111");
        Team team = createTeam(owner, "FC 새벽", "서울 강서구");
        // 팀은 그대로 두고 그 동의 기록만 지운다 — 개편 전 계정의 모양이다
        transactionTemplate.executeWithoutResult(status -> consentRepository
                .findByUserIdAndConsentType(owner.getId(), ConsentType.CONTACT_SHARING)
                .ifPresent(consentRepository::delete));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.consentRequired").value(true))
                .andExpect(jsonPath("$.pendingConsents",
                        org.hamcrest.Matchers.contains("contactSharing")));
        assertThat(team.getId()).isNotNull();
    }

    private ResultActions submit(User user, String body) throws Exception {
        return mockMvc.perform(put("/api/users/me/consents")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
