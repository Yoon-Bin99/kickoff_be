package com.kickoff.be.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 웹 OAuth 일회용 코드 교환 (계약서 §3-1, v1.26.0).
 *
 * <b>왜 나눴는가.</b> 앱은 복귀 URL 이 {@code kickoff://} 딥링크라 토큰을 쿼리로 실어도
 * OS 가 앱에 넘기고 끝난다. 웹은 같은 URL 이 주소창에 뜨고 브라우저 히스토리에 남으며,
 * 그 페이지가 외부 자원을 하나라도 부르면 {@code Referer} 헤더로 URL 전체가 제3자에게
 * 간다. refresh 는 30일짜리라 access 와 무게가 다르다.
 *
 * <b>이 클래스에서 가장 중요한 단언은 "웹 복귀에는 토큰이 없다"이다.</b> 나머지는 그
 * 대체 경로가 제대로 도는지를 볼 뿐이고, 저 단언이 깨지면 기능은 멀쩡히 도는 채로
 * 애초에 막으려던 것이 그대로 새어 나간다.
 */
class WebOAuthExchangeTest extends IntegrationTestSupport {

    private static final String WEB_REDIRECT = "http://localhost:3000/oauth";
    private static final String APP_REDIRECT = "exp://192.168.0.10:8081/--/auth";

    @Test
    @DisplayName("웹 복귀에는 토큰이 없고 일회용 코드만 실린다")
    void webRedirectCarriesCodeNotTokens() throws Exception {
        kakaoStub.willReturn("kakao-web-1", "웹사용자");

        Map<String, String> params = loginWith(WEB_REDIRECT);

        assertThat(params.get("code")).as("교환용 코드").isNotBlank();
        assertThat(params.get("isNewUser")).isEqualTo("true");
        assertThat(params)
                .as("토큰이 URL 에 실리면 히스토리·Referer 로 새어 나간다")
                .doesNotContainKeys("token", "refreshToken");
    }

    /**
     * 이미 배포된 앱이 깨지지 않는지 본다. 분기가 스킴 기준이라 커스텀 스킴은 예전
     * 그대로여야 하고, 여기가 바뀌면 <b>사용자가 앱을 업데이트하기 전까지 로그인이 막힌다.</b>
     */
    @Test
    @DisplayName("앱 복귀는 예전 그대로 토큰을 싣는다 — 기존 빌드가 깨지면 안 된다")
    void appRedirectKeepsTokens() throws Exception {
        kakaoStub.willReturn("kakao-app-1", "앱사용자");

        Map<String, String> params = loginWith(APP_REDIRECT);

        assertThat(params.get("token")).isNotBlank();
        assertThat(params.get("refreshToken")).isNotBlank();
        assertThat(params).doesNotContainKey("code");
    }

    @Test
    @DisplayName("코드를 교환하면 앱이 받던 것과 같은 세 값이 온다")
    void exchangeReturnsTokens() throws Exception {
        kakaoStub.willReturn("kakao-web-2", "웹사용자");
        String code = loginWith(WEB_REDIRECT).get("code");

        exchange(code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.isNewUser").value(true));
    }

    /** 받은 토큰이 실제로 통해야 한다. 형태만 맞고 안 통하면 화면은 로그인 직후 튕긴다. */
    @Test
    @DisplayName("교환으로 받은 토큰으로 인증 요청이 통한다")
    void exchangedTokenWorks() throws Exception {
        kakaoStub.willReturn("kakao-web-3", "웹사용자");
        String code = loginWith(WEB_REDIRECT).get("code");

        String body = exchange(code).andReturn().getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("웹사용자"));
    }

    /**
     * <b>1회용이 이 설계의 핵심이다.</b> 코드는 주소창에 남아 히스토리에도 들어간다 —
     * 재사용이 되면 토큰을 URL 에서 뺀 의미가 사라진다.
     */
    @Test
    @DisplayName("같은 코드를 두 번 쓰면 401 — 1회용이다")
    void codeIsSingleUse() throws Exception {
        kakaoStub.willReturn("kakao-web-4", "웹사용자");
        String code = loginWith(WEB_REDIRECT).get("code");

        exchange(code).andExpect(status().isOk());

        exchange(code)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_CODE_INVALID"));
    }

    @Test
    @DisplayName("없는 코드는 401 — 있는 코드와 구분해 주지 않는다")
    void unknownCodeIsRejected() throws Exception {
        exchange("이런-코드는-없다")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_CODE_INVALID"));
    }

    @Test
    @DisplayName("빈 코드는 400 — 형식 자체가 틀린 건 검증 단계에서 걸린다")
    void blankCodeIsBadRequest() throws Exception {
        exchange("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("인증 없이 부를 수 있다 — 토큰을 받으러 오는 호출이다")
    void needsNoAuthentication() throws Exception {
        exchange("아무거나").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OAUTH_CODE_INVALID"));
        // 401 이지만 UNAUTHORIZED(인증 필요)가 아니라 OAUTH_CODE_INVALID 다 —
        // 시큐리티에 막힌 게 아니라 컨트롤러까지 도달했다는 뜻이다.
    }

    /**
     * 실패는 스킴과 무관하게 {@code ?error=} 다. 여기가 갈리면 웹 FE 가 실패를 못 알아보고
     * 빈 화면에 머문다 — 사용자에게는 "눌렀는데 아무 일도 안 일어남"으로 보인다.
     */
    @Test
    @DisplayName("실패는 웹·앱 양쪽 다 ?error= 로 돌아온다")
    void failureUsesErrorParamOnBothSchemes() throws Exception {
        assertThat(failureQuery(WEB_REDIRECT))
                .containsEntry("error", "OAUTH_FAILED")
                .doesNotContainKeys("code", "token", "refreshToken");
        assertThat(failureQuery(APP_REDIRECT))
                .containsEntry("error", "OAUTH_FAILED")
                .doesNotContainKeys("code", "token", "refreshToken");
    }

    /**
     * 허용 목록은 <b>완전 일치</b>다({@code matches()}). 웹 복귀 URL 을 등록할 때 이걸
     * 모르면 "패턴을 넣었는데 400 이 난다"로 막힌다 — 쿼리를 달아 보내는 순간 안 맞는다.
     */
    @Test
    @DisplayName("웹 복귀 URL 은 완전 일치라야 한다 — 쿼리를 달면 안 맞는다")
    void allowListRequiresExactMatch() throws Exception {
        // test 프로파일 목록에 http://localhost:* 가 있어 경로까지는 통과한다
        authorizeWith("http://localhost:3000/oauth").andExpect(status().isFound());

        // 목록에 없는 호스트는 400
        authorizeWith("https://evil.example/oauth")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private Map<String, String> failureQuery(String redirect) throws Exception {
        authorizeWith(redirect).andExpect(status().isFound());
        String state = kakaoStub.lastState();
        // 제공자가 거부한 경우 — code 없이 error 만 돌아온다
        String location = mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("state", state)
                        .param("error", "access_denied"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        return UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap();
    }

    private ResultActions authorizeWith(String redirect) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/kakao/authorize").param("redirect", redirect));
    }

    private ResultActions exchange(String code) throws Exception {
        return mockMvc.perform(post("/api/auth/oauth/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    private Map<String, String> loginWith(String redirect) throws Exception {
        authorizeWith(redirect).andExpect(status().isFound());
        String state = kakaoStub.lastState();

        String location = mockMvc.perform(get("/api/auth/oauth/kakao/callback")
                        .param("code", "code-" + state.hashCode())
                        .param("state", state))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).isNotNull();
        return UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().toSingleValueMap();
    }
}
