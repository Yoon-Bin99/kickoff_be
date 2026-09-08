package com.kickoff.be.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 약관·개인정보처리방침 동의 (계약서 §3-1, v1.25.0).
 *
 * <b>이 기능이 생긴 이유가 곧 검증 대상이다.</b> 이메일 가입만 {@code termsAgreed} 를 받아
 * 시각을 남겼고 소셜 가입은 그 자리를 통과하지 않아서, <b>동의 근거가 없는 계정</b>이 쌓여
 * 있었다. 그래서 "소셜 픽스처는 null 이다"가 이 클래스에서 가장 중요한 단언이다 — 서버가
 * 어딘가에서 값을 채워 주면 FE 게이트가 영영 안 뜨고, 문제는 조용히 그대로 남는다.
 *
 * 게이트 자체는 FE 가 건다. 서버가 동의 전 호출을 막지 않는 것도 의도다 — 막으면 기존
 * 소셜 사용자 전원이 한 번에 401 로 튕겨 나간다.
 */
class TermsAgreementGateTest extends IntegrationTestSupport {

    /**
     * 소셜 가입 계정은 이메일도 비밀번호도 없이 만들어진다. 동의 절차를 지나지 않았으므로
     * null 이어야 하고, 이 null 이 FE 가 동의 화면을 띄우는 유일한 신호다.
     */
    @Test
    @DisplayName("소셜 가입 계정은 동의 시각이 null 이다")
    void socialAccountHasNoAgreement() throws Exception {
        User social = createUserWithoutEmail("카카오가입자", "010-2222-2222");

        me(social).andExpect(status().isOk())
                .andExpect(jsonPath("$.termsAgreedAt").isEmpty());
    }

    @Test
    @DisplayName("동의를 기록하면 204 이고 시각이 채워진다")
    void agreeRecordsTime() throws Exception {
        User social = createUserWithoutEmail("카카오가입자", "010-3333-3333");
        // 초 단위로 자른다. DB 가 시각을 마이크로초까지만 저장해서, 나노초를 가진
        // OffsetDateTime.now() 와 그대로 비교하면 <b>방금 저장한 값이 "더 이르다"</b>고
        // 나온다 (100 나노초 차이로 실패한다). H2 도 PostgreSQL 도 같은 성질이라
        // 자르지 않으면 어느 DB 에서든 간헐적으로 깨진다.
        OffsetDateTime before = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        agree(social).andExpect(status().isNoContent());

        me(social).andExpect(jsonPath("$.termsAgreedAt").isNotEmpty());
        OffsetDateTime recorded = userRepository.findById(social.getId())
                .orElseThrow().getTermsAgreedAt();
        assertThat(recorded).isNotNull();
        assertThat(recorded).isAfterOrEqualTo(before);
    }

    /**
     * 멱등. 동의 화면에서 네트워크가 끊겨 재시도하는 경우가 정상 경로에 있어서,
     * 두 번째 호출이 실패하면 사용자가 동의 화면에 갇힌다.
     */
    @Test
    @DisplayName("두 번 호출해도 204 이고 시각이 최신으로 갱신된다")
    void agreeIsIdempotent() throws Exception {
        User social = createUserWithoutEmail("카카오가입자", "010-4444-4444");

        agree(social).andExpect(status().isNoContent());
        OffsetDateTime first = userRepository.findById(social.getId())
                .orElseThrow().getTermsAgreedAt();

        agree(social).andExpect(status().isNoContent());
        OffsetDateTime second = userRepository.findById(social.getId())
                .orElseThrow().getTermsAgreedAt();

        assertThat(second).isAfterOrEqualTo(first);
    }

    @Test
    @DisplayName("토큰 없이 부르면 401")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(post("/api/users/me/terms-agreement"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions agree(User user) throws Exception {
        return mockMvc.perform(post("/api/users/me/terms-agreement")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }

    private ResultActions me(User user) throws Exception {
        return mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)));
    }
}
