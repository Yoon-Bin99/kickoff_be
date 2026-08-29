package com.kickoff.be.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.support.StubOAuthClient;
import com.kickoff.be.user.entity.User;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인 (계약서 §3-1). 제공자 통신은 스텁으로 대체하고 BE 가 책임지는 부분만 본다.
 *
 * v1.3.4 부터 계정 결정은 두 규칙뿐이다 — 연동 이력이 있으면 그 계정, 없으면 무조건 신규.
 * 이메일 자동 연동은 폐지됐고 소셜 계정의 email 은 언제나 null 이다.
 */
class SocialLoginTest extends IntegrationTestSupport {

    private static final String REDIRECT = "exp://192.168.0.10:8081/--/auth";

    @Test
    @DisplayName("연동 이력이 없으면 무조건 새 계정을 만든다 — email·비밀번호·전화번호 전부 없이 (규칙 2)")
    void createsNewAccountWhenNotLinked() throws Exception {
        kakaoStub.willReturn("kakao-1", "카카오사용자");

        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("true");
        assertThat(params.get("token")).isNotBlank();

        User created = userRepository.findById(tokenProvider.parseUserId(params.get("token")))
                .orElseThrow();
        assertThat(created.getEmail()).isNull();
        assertThat(created.getNickname()).isEqualTo("카카오사용자");
        assertThat(created.hasPassword()).isFalse();
        assertThat(created.hasPhone()).isFalse();
        assertThat(socialAccountRepository.findProvidersByUserId(created.getId()))
                .containsExactly(AuthProvider.KAKAO);
    }

    @Test
    @DisplayName("연동 이력이 있으면 그 계정으로 로그인한다 — 두 번째부터는 신규가 아니다 (규칙 1)")
    void reusesLinkedAccount() throws Exception {
        kakaoStub.willReturn("kakao-1", "카카오사용자");
        Long userId = tokenProvider.parseUserId(login().get("token"));

        kakaoStub.willReturn("kakao-1", "카카오사용자");
        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("false");
        assertThat(tokenProvider.parseUserId(params.get("token"))).isEqualTo(userId);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(socialAccountRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("이메일이 같은 기존 계정이 있어도 합치지 않는다 — 자동 연동 폐지 (v1.3.4)")
    void neverLinksToExistingEmailAccount() throws Exception {
        User existing = createUser("kim@example.com", "김주장", "010-1111-1111");
        kakaoStub.willReturn("kakao-9", "김주장");

        Map<String, String> params = login();

        assertThat(params.get("isNewUser")).isEqualTo("true");
        assertThat(tokenProvider.parseUserId(params.get("token"))).isNotEqualTo(existing.getId());
        assertThat(userRepository.count()).isEqualTo(2);

        // 기존 계정은 아무 영향도 받지 않는다 — 비번 로그인도 그대로 된다
        User after = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(after.getEmail()).isEqualTo("kim@example.com");
        assertThat(socialAccountRepository.findProvidersByUserId(existing.getId())).isEmpty();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"kim@example.com\",\"password\":\"pass1234!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("같은 사람이 카카오와 네이버로 들어오면 서로 다른 계정이 된다 (v1.3.4)")
    void eachProviderGetsItsOwnAccount() throws Exception {
        kakaoStub.willReturn("kakao-x", "같은사람");
        Long kakaoUser = tokenProvider.parseUserId(login().get("token"));

        naverStub.willReturn("naver-x", "같은사람");
        Long naverUser = tokenProvider.parseUserId(login("naver", naverStub).get("token"));

        assertThat(kakaoUser).isNotEqualTo(naverUser);
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(socialAccountRepository.findProvidersByUserId(kakaoUser))
                .containsExactly(AuthProvider.KAKAO);
        assertThat(socialAccountRepository.findProvidersByUserId(naverUser))
                .containsExactly(AuthProvider.NAVER);
    }

    @Test
    @DisplayName("email 이 null 인 계정이 여러 개여도 유니크 제약에 걸리지 않는다")
    void multipleAccountsCanHaveNullEmail() throws Exception {
        kakaoStub.willReturn("kakao-a", "가나");
        Long first = tokenProvider.parseUserId(login().get("token"));
        kakaoStub.willReturn("kakao-b", "다라");
        Long second = tokenProvider.parseUserId(login().get("token"));
        naverStub.willReturn("naver-c", "마바");
        Long third = tokenProvider.parseUserId(login("naver", naverStub).get("token"));

        assertThat(userRepository.count()).isEqualTo(3);
        for (Long id : new Long[]{first, second, third}) {
            assertThat(userRepository.findById(id).orElseThrow().getEmail()).isNull();
        }
    }

    @Test
    @DisplayName("닉네임이 겹치면 뒤에 숫자를 붙인다")
    void appendsNumberWhenNicknameTaken() throws Exception {
        createUser("other@example.com", "김주장", "010-2222-2222");
        kakaoStub.willReturn("kakao-4", "김주장");

        Long userId = tokenProvider.parseUserId(login().get("token"));

        assertThat(userRepository.findById(userId).orElseThrow().getNickname())
                .isEqualTo("김주장2");
    }

    @Test
    @DisplayName("제공자가 닉네임을 안 주면 기본 닉네임으로 만든다")
    void fallsBackWhenProviderGivesNoNickname() throws Exception {
        kakaoStub.willReturn("kakao-5", null);

        Long userId = tokenProvider.parseUserId(login().get("token"));

        assertThat(userRepository.findById(userId).orElseThrow().getNickname())
                .isEqualTo("킥오프사용자");
    }

    @Test
    @DisplayName("복귀 주소가 정상이면 비활성 제공자도 302 로 되돌린다 — 인앱 브라우저는 JSON 을 못 읽는다 (v1.3.3)")
    void inactiveProviderRedirectsWithError() throws Exception {
        // 구글은 계약서에 값만 예약돼 있고(§8), facebook 은 아예 없는 이름이다.
        // 둘 다 복귀 주소는 멀쩡하므로 FE 가 읽을 수 있는 형태로 돌려보내야 한다.
        for (String provider : new String[]{"google", "facebook"}) {
            String location = mockMvc.perform(get("/api/auth/oauth/{p}/authorize", provider)
                            .param("redirect", REDIRECT))
                    .andExpect(status().isFound())
                    .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
            assertThat(location).startsWith(REDIRECT + "?");
            assertThat(location).contains("error=UNSUPPORTED_PROVIDER");
        }
    }

    @Test
    @DisplayName("복귀 주소가 목록 밖이면 제공자를 보기도 전에 400 이다 — 302 할 곳이 없다 (v1.3.3)")
    void redirectIsValidatedBeforeProvider() throws Exception {
        // 제공자까지 비활성인 조합이라도 redirect 쪽이 먼저 걸린다.
        mockMvc.perform(get("/api/auth/oauth/google/authorize")
                        .param("redirect", "https://evil.example/steal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("redirect"));

        // 활성 제공자여도 마찬가지다 — 토큰을 남의 서버로 보내는 통로가 된다
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                        .param("redirect", "https://evil.example/steal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // 허용 목록의 앞부분만 흉내 낸 주소도 막힌다
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                        .param("redirect", "https://evil.example/?x=exp://"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
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
                            Matchers.containsString("state=")));
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
        kakaoStub.willReturn("kakao-6", "리플레이");
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
    @DisplayName("소셜 계정은 비밀번호가 없어 이메일/비번 로그인 경로로 들어올 수 없다")
    void socialAccountCannotLoginWithPassword() throws Exception {
        kakaoStub.willReturn("kakao-7", "소셜");
        login();

        // email 이 null 이라 애초에 지목할 수단이 없고, 존재를 알려주지도 않는다
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"social@example.com\",\"password\":\"pass1234!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @DisplayName("발급된 토큰으로 /api/auth/me 가 열린다 — email·phone 은 null, authProviders 는 채워진다")
    void issuedTokenWorksAndExposesProviders() throws Exception {
        kakaoStub.willReturn("kakao-8", "미가입자");
        String token = login().get("token");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.nickname").value("미가입자"))
                .andExpect(jsonPath("$.phone").isEmpty())
                .andExpect(jsonPath("$.hasTeam").value(false))
                .andExpect(jsonPath("$.authProviders[0]").value("KAKAO"));
    }

    @Test
    @DisplayName("Expo Go 의 실제 복귀 URL 로 한 바퀴 — 스킴 뒤 host:port 와 경로가 붙어도 통과한다")
    void worksWithRealExpoRedirectUrl() throws Exception {
        // FE 가 실제로 넘기는 형태. 허용 목록이 정확 일치였다면 여기서 막혔을 것이다.
        String expoRedirect = "exp://192.168.35.95:8081/--/oauth";
        kakaoStub.willReturn("kakao-expo", "엑스포");

        String state = authorize("kakao", kakaoStub, expoRedirect);
        String location = callback(state).andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        // 복귀 주소는 그대로 두고 쿼리만 덧붙는다
        assertThat(location).startsWith(expoRedirect + "?");
        assertThat(location).contains("isNewUser=true");
        Map<String, String> params = UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap();
        assertThat(params.get("token")).isNotBlank();
    }

    /** authorize → callback 한 바퀴를 돌고 복귀 URL 의 쿼리를 돌려준다. */
    @Test
    @DisplayName("콜백 redirect 에 refreshToken 도 실린다 — 소셜도 자동 로그인이 된다 (v1.7.0)")
    void callbackCarriesRefreshToken() throws Exception {
        kakaoStub.willReturn("kakao-refresh", "카카오사용자");

        Map<String, String> params = login();

        assertThat(params.get("refreshToken")).isNotBlank();
        assertThat(params.get("token")).isNotBlank();
        assertThat(params.get("refreshToken")).isNotEqualTo(params.get("token"));

        // 그 refreshToken 이 실제로 통해야 한다. 쿼리에 실렸다는 것만으로는
        // 서버가 해시를 저장했는지 알 수 없다.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\": \"" + params.get("refreshToken") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    private Map<String, String> login() throws Exception {
        return login("kakao", kakaoStub);
    }

    private Map<String, String> login(String provider, StubOAuthClient stub) throws Exception {
        String state = authorize(provider, stub, REDIRECT);
        return queryOf(callback(provider, state).andExpect(status().isFound()));
    }

    private String authorize(String redirect) throws Exception {
        return authorize("kakao", kakaoStub, redirect);
    }

    private String authorize(String provider, StubOAuthClient stub, String redirect)
            throws Exception {
        mockMvc.perform(get("/api/auth/oauth/{p}/authorize", provider)
                        .param("redirect", redirect))
                .andExpect(status().isFound());
        return stub.lastState();
    }

    private ResultActions callback(String state) throws Exception {
        return callback("kakao", state);
    }

    private ResultActions callback(String provider, String state) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/{p}/callback", provider)
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
