package com.kickoff.be.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 팀을 만들 때 받는 연락처 제공 동의 (계약서 §3, v1.34.1).
 *
 * <b>가입이 아니라 여기서 받는 이유</b>: 매칭이 성사되면 전화번호가 상대 팀 감독에게
 * 제공되는데(제3자 제공, 제17조) 그 일이 일어나는 사람은 <b>감독뿐</b>이다. 팀원으로만
 * 활동할 가입자에게 필수로 요구하면 받을 필요 없는 동의를 강요하는 것이 된다.
 *
 * 시행일을 뒤로 밀어 "개정 후 재동의"를 함께 본다 — 그 판정이 이 한 설정값에 달려 있다.
 */
@TestPropertySource(properties = "kickoff.legal.effective-at=2026-09-08T00:00:00+09:00")
class ConsentTeamOwnerTest extends IntegrationTestSupport {

    private User owner;

    @BeforeEach
    void setUpOwner() {
        owner = createUser("owner@example.com", "김주장", "010-1111-1111");
    }

    @Test
    @DisplayName("팀을 만들면 연락처 제공 동의가 기록된다")
    void creatingTeamRecordsContactSharing() throws Exception {
        createTeam("true").andExpect(status().isCreated());

        assertThat(consentRepository
                .findByUserIdAndConsentType(owner.getId(), ConsentType.CONTACT_SHARING))
                .isPresent();
    }

    @Test
    @DisplayName("동의 없이는 팀을 만들 수 없다")
    void teamNeedsConsent() throws Exception {
        createTeam("false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'contactSharingConsent')]")
                        .exists());

        assertThat(teamRepository.findByOwnerId(owner.getId()))
                .as("동의가 없으면 팀도 만들어지지 않는다").isEmpty();
    }

    /** 누락과 거부를 가린다 — 누락은 구버전 앱이 보내는 모양이다. */
    @Test
    @DisplayName("필드를 아예 안 보내도 400 이고 안내가 다르다")
    void missingConsentAsksForUpdate() throws Exception {
        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"FC 새벽","region":"서울 강서구","skillLevel":"INTERMEDIATE",
                                 "ageGroup":"THIRTIES","memberCount":18}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'contactSharingConsent')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("업데이트"))));
    }

    @Test
    @DisplayName("팀을 만든 뒤에도 동의 화면은 필요 없다")
    void ownerNeedsNoConsentScreenAfterCreating() throws Exception {
        createTeam("true").andExpect(status().isCreated());

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(jsonPath("$.consentRequired").value(false))
                .andExpect(jsonPath("$.pendingConsents").isEmpty());
    }

    /**
     * 팀 정보에 저장되지 않고 응답에도 없다 (계약서 §3) — 팀의 속성이 아니라 그 사람의
     * 동의 기록이다.
     */
    @Test
    @DisplayName("응답에 동의 값이 실리지 않는다")
    void consentIsNotEchoedInTeamResponse() throws Exception {
        createTeam("true")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactSharingConsent").doesNotExist());
    }

    private ResultActions createTeam(String consent) throws Exception {
        return mockMvc.perform(post("/api/teams")
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"FC 새벽","region":"서울 강서구","skillLevel":"INTERMEDIATE",
                         "ageGroup":"THIRTIES","memberCount":18,"contactSharingConsent":%s}
                        """.formatted(consent)));
    }
}
