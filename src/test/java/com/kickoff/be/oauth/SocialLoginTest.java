package com.kickoff.be.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인 (계약서 §3-1). 제공자 통신은 스텁으로 대체하고, BE 가 책임지는 부분만 본다 —
 * 계정 결정 4규칙, state, 복귀 주소 허용 목록.
 */
class SocialLoginTest extends IntegrationTestSupport {

    private static final String REDIRECT = "exp://192.168.0.10:8081/--/auth";

    @Test
    @DisplayName("연동도 같은 이메일도 없으면 새 계정을 만든다 — 비밀번호도 전화번호도 없이 (규칙 3)")
    void createsNewAccountWhenNothingMatches() throws Exception {
        kakaoStub.willReturn("kakao-1", "new@example.com", "카카오사용자");

        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("true");
        assertThat(params.get("token")).isNotBlank();

        User created = userRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(created.getNickname()).isEqualTo("카카오사용자");
        assertThat(created.hasPassword()).isFalse();
        assertThat(created.hasPhone()).isFalse();
        assertThat(socialAccountRepository.findProvidersByUserId(created.getId()))
                .containsExactly(AuthProvider.KAKAO);
    }

    @Test
    @DisplayName("연동 이력이 있으면 그 계정으로 로그인한다 — 두 번째부터는 신규가 아니다 (규칙 1)")
    void reusesLinkedAccount() throws Exception {
        kakaoStub.willReturn("kakao-1", "new@example.com", "카카오사용자");
        login();
        long userId = userRepository.findByEmail("new@example.com").orElseThrow().getId();

        kakaoStub.willReturn("kakao-1", "new@example.com", "카카오사용자");
        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("false");
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
        assertThat(tokenProvider.parseUserId(params.get("token"))).isEqualTo(userId);
    }

    @Test
    @DisplayName("이메일이 같은 기존 계정이 있으면 자동 연동한다 — 새 계정을 만들지 않는다 (규칙 2)")
    void linksToExistingAccountWithSameEmail() throws Exception {
        User existing = createUser("kim@example.com", "김주장", "010-1111-1111");
        kakaoStub.willReturn("kakao-9", "kim@example.com", "카카오김주장");

        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("false");
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(tokenProvider.parseUserId(params.get("token"))).isEqualTo(existing.getId());
        // 기존 닉네임과 전화번호는 소셜 프로필로 덮이지 않는다
        User after = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getNickname()).isEqualTo("김주장");
        assertThat(after.getPhone()).isEqualTo("010-1111-1111");
        assertThat(socialAccountRepository.findProvidersByUserId(existing.getId()))
                .containsExactly(AuthProvider.KAKAO);
    }

    @Test
    @DisplayName("제공자가 이메일을 주지 않으면 EMAIL_CONSENT_REQUIRED 로 되돌린다 (규칙 4)")
    void failsWhenProviderGivesNoEmail() throws Exception {
        kakaoStub.willReturn("kakao-2", null, "이메일없음");

        Map<String, String> params = login();

        assertThat(params.get("error")).isEqualTo("EMAIL_CONSENT_REQUIRED");
        assertThat(params).doesNotContainKey("token");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 연동된 계정은 이메일 동의를 거둬도 계속 로그인된다 — 규칙 1이 규칙 4보다 앞선다")
    void linkedAccountSurvivesEmailConsentWithdrawal() throws Exception {
        kakaoStub.willReturn("kakao-3", "keep@example.com", "유지");
        login();

        kakaoStub.willReturn("kakao-3", null, "유지");
        Map<String, String> params = login();

        assertThat(params.get("error")).isNull();
        assertThat(params.get("isNewUser")).isEqualTo("false");
    }

    @Test
    @DisplayName("닉네임이 겹치면 뒤에 숫자를 붙인다 (규칙 3)")
    void appendsNumberWhenNicknameTaken() throws Exception {
        createUser("other@example.com", "김주장", "010-2222-2222");
        kakaoStub.willReturn("kakao-4", "new@example.com", "김주장");

        login();

        assertThat(userRepository.findByEmail("new@example.com").orElseThrow().getNickname())
                .isEqualTo("김주장2");
    }

    @Test
    @DisplayName("제공자가 닉네임을 안 주면 기본 닉네임으로 만든다")
    void fallsBackWhenProviderGivesNoNickname() throws Exception {
        kakaoStub.willReturn("kakao-5", "noname@example.com", null);

        login();

        assertThat(userRepository.findByEmail("noname@example.com").orElseThrow().getNickname())
                .isEqualTo("킥오프사용자");
    }

    @Test
    @DisplayName("키가 없는 제공자와 예약만 된 제공자는 400 UNSUPPORTED_PROVIDER")
    void inactiveProvidersAreRejected() throws Exception {
        // 네이버는 키가 없어 등록되지 않았다
        mockMvc.perform(get("/api/auth/oauth/naver/authorize").param("redirect", REDIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROVIDER"));

        // 구글은 계약서에 값만 예약돼 있다
        mockMvc.perform(get("/api/auth/oauth/google/authorize").param("redirect", REDIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROVIDER"));

        // 아예 없는 이름
        mockMvc.perform(get("/api/auth/oauth/facebook/authorize").param("redirect", REDIRECT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PROVIDER"));
    }

    @Test
    @DisplayName("허용 목록에 없는 복귀 주소는 거부한다 — 토큰을 남의 서버로 보내는 통로가 된다")
    void rejectsRedirectOutsideAllowList() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                        .param("redirect", "https://evil.example/steal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("redirect"));

        // 허용 목록의 앞부분만 흉내 낸 주소도 막힌다
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                        .param("redirect", "https://evil.example/?x=exp://"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("복귀 주소를 제공자보다 먼저 본다 — 둘 다 틀리면 redirect 쪽이 나간다")
    void redirectIsValidatedBeforeProvider() throws Exception {
        // 키가 없는 동안 제공자 검사가 앞서면 허용 목록 위반이 UNSUPPORTED_PROVIDER 로
        // 덮여버려, FE 가 이 실패를 볼 방법이 없어진다.
        mockMvc.perform(get("/api/auth/oauth/naver/authorize")
                        .param("redirect", "https://evil.example/steal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("redirect"));
    }

    @Test
    @DisplayName("redirect 없이 호출하면 400")
    void redirectIsRequired() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("허용 목록에 있는 주소는 통과하고, 제공자로 302 하면서 state 를 싣는다")
    void authorizeRedirectsToProviderWithState() throws Exception {
        for (String allowed : new String[]{REDIRECT, "kickoff://auth", "http://localhost:8081/cb"}) {
            mockMvc.perform(get("/api/auth/oauth/kakao/authorize").param("redirect", allowed))
                    .andExpect(status().isFound())
                    .andExpect(header().string(HttpHeaders.LOCATION,
                            org.hamcrest.Matchers.containsString("state=")));
        }
        assertThat(kakaoStub.lastState()).isNotBlank();
        // 콜백 URI 는 요청 오리진에서 만들어져 제공자에게 넘어간다
        assertThat(kakaoStub.lastCallbackUri()).endsWith("/api/auth/oauth/kakao/callback");
    }

    @Test
    @DisplayName("모르는 state 로 콜백이 오면 401 — 돌아갈 주소를 모르니 302 하지 않는다")
    void unknownStateIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "code-1")
                        .param("state", "never-issued"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_FAILED"));
    }

    @Test
    @DisplayName("같은 state 는 두 번 쓸 수 없다 — 일회용이다")
    void stateCannotBeReplayed() throws Exception {
        kakaoStub.willReturn("kakao-6", "replay@example.com", "리플레이");
        String state = authorize(REDIRECT);

        callback(state).andExpect(status().isFound());
        callback(state)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_FAILED"));
    }

    @Test
    @DisplayName("제공자가 거부하거나 호출이 실패하면 error 를 달아 앱으로 되돌린다")
    void providerFailureRedirectsWithError() throws Exception {
        String deniedState = authorize(REDIRECT);
        Map<String, String> denied = queryOf(mockMvc.perform(
                        get("/api/auth/oauth/kakao/callback")
                                .param("state", deniedState)
                                .param("error", "access_denied"))
                .andExpect(status().isFound()));
        assertThat(denied.get("error")).isEqualTo("OAUTH_FAILED");

        kakaoStub.willFail();
        String failedState = authorize(REDIRECT);
        Map<String, String> failed = queryOf(callback(failedState).andExpect(status().isFound()));
        assertThat(failed.get("error")).isEqualTo("OAUTH_FAILED");
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("소셜로만 가입한 계정은 이메일/비번 로그인이 401 LOGIN_FAILED — 존재를 알려주지 않는다")
    void socialOnlyAccountCannotLoginWithPassword() throws Exception {
        kakaoStub.willReturn("kakao-7", "social@example.com", "소셜");
        login();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"social@example.com\",\"password\":\"pass1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("발급된 토큰으로 /api/auth/me 가 열리고 authProviders 가 채워진다")
    void issuedTokenWorksAndExposesProviders() throws Exception {
        kakaoStub.willReturn("kakao-8", "me@example.com", "미");
        String token = login().get("token");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.hasTeam").value(false))
                .andExpect(jsonPath("$.authProviders[0]").value("KAKAO"));
    }

    @Test
    @DisplayName("Expo Go 의 실제 복귀 URL 로 한 바퀴 — 스킴 뒤 host:port 와 경로가 붙어도 통과한다")
    void worksWithRealExpoRedirectUrl() throws Exception {
        // FE 가 실제로 넘기는 형태. 허용 목록이 정확 일치였다면 여기서 막혔을 것이다.
        String expoRedirect = "exp://192.168.35.95:8081/--/oauth";
        kakaoStub.willReturn("kakao-expo", "expo@example.com", "엑스포");

        String state = authorize(expoRedirect);
        String location = callback(state).andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        // 복귀 주소는 그대로 두고 쿼리만 덧붙는다
        assertThat(location).startsWith(expoRedirect + "?");
        assertThat(location).contains("isNewUser=true");
        Map<String, String> params = UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap();
        assertThat(params.get("token")).isNotBlank();
        assertThat(userRepository.findByEmail("expo@example.com").orElseThrow().getPhone())
                .isNull();
    }

    /** authorize → callback 한 바퀴를 돌고 복귀 URL 의 쿼리를 돌려준다. */
    private Map<String, String> login() throws Exception {
        String state = authorize(REDIRECT);
        return queryOf(callback(state).andExpect(status().isFound()));
    }

    private String authorize(String redirect) throws Exception {
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize").param("redirect", redirect))
                .andExpect(status().isFound());
        return kakaoStub.lastState();
    }

    private ResultActions callback(String state) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                .param("code", "code-" + state.hashCode())
                .param("state", state));
    }

    private Map<String, String> queryOf(ResultActions actions) throws Exception {
        String location = actions.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        return UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap();
    }
}
