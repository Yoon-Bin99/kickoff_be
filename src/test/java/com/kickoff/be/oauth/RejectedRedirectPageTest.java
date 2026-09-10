package com.kickoff.be.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 허용 목록 밖 복귀 주소의 400 을 브라우저에는 HTML 로 (계약서 §3-1, v1.26.1).
 *
 * <b>이 API 는 사람이 직접 보는 유일한 자리다.</b> 앱은 인앱 브라우저, 웹은 페이지째
 * 이동이라 응답이 그대로 화면이 된다. JSON 이 나가면 사용자는 API 도메인에서 날 JSON 을
 * 보고 갇힌다 — 돌아갈 링크조차 없다(그 redirect 는 우리가 신뢰하지 않기로 판단한 값이라
 * 링크로 걸 수 없다).
 *
 * <b>redirect 값을 되비추지 않는 것이 이 페이지의 핵심이다.</b> 링크로 걸면 허용 목록
 * 검사가 무의미해지고(open redirect), 본문에 그대로 찍으면 스크립트가 섞여 들어온다.
 */
class RejectedRedirectPageTest extends IntegrationTestSupport {

    private static final String REJECTED = "https://evil.example/steal";

    @Test
    @DisplayName("브라우저(Accept: text/html)에는 400 + HTML 안내가 나간다")
    void browserGetsHtml() throws Exception {
        authorize(REJECTED, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "허용되지 않은 복귀 주소입니다. 앱이나 사이트로 돌아가 다시 시도해 주세요.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("history.back()")));
    }

    /**
     * 한글이 깨지지 않는지 따로 본다. 응답 charset 과 빌드의 소스 인코딩이 어긋나면
     * 상태·헤더는 멀쩡한 채 <b>화면에만 물음표가 찍힌다</b> — 안내문이 목적인 페이지라
     * 그러면 기능이 통째로 없는 것과 같다.
     */
    @Test
    @DisplayName("안내 문구의 한글이 깨지지 않는다")
    void koreanSurvivesEncoding() throws Exception {
        String body = authorize(REJECTED, "text/html").andReturn()
                .getResponse().getContentAsString();

        assertThat(body).contains("허용되지 않은 복귀 주소입니다");
        assertThat(body).doesNotContain("?????").doesNotContain("�");
    }

    @Test
    @DisplayName("Accept 가 JSON 이면 현행 JSON 그대로 — 다른 API 형식은 안 바뀐다")
    void jsonClientGetsJson() throws Exception {
        authorize(REJECTED, "application/json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("redirect"));
    }

    @Test
    @DisplayName("Accept 가 아예 없어도 JSON — 기존 테스트·curl 이 그대로 돈다")
    void noAcceptHeaderGetsJson() throws Exception {
        mockMvc.perform(get("/api/auth/oauth/kakao/authorize").param("redirect", REJECTED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    /**
     * <b>거부한 주소를 페이지에 되비추면 안 된다.</b> 링크로 걸면 open redirect 가 되고,
     * 본문에 찍으면 그 값에 섞인 스크립트가 사용자 브라우저에서 돈다. 여기서는 값을
     * 아예 쓰지 않는 쪽으로 막는다 — 이스케이프에 기대지 않는다.
     */
    @Test
    @DisplayName("거부한 redirect 값이 페이지에 나타나지 않는다")
    void doesNotReflectRedirectValue() throws Exception {
        String xss = "https://evil.example/\"><script>alert(1)</script>";

        String body = authorize(xss, "text/html").andReturn()
                .getResponse().getContentAsString();

        assertThat(body).doesNotContain("evil.example");
        assertThat(body).doesNotContain("script>alert");
    }

    /** 외부 자원을 부르면 그 요청의 Referer 로 문제의 URL 전체가 제3자에게 나간다. */
    @Test
    @DisplayName("외부 자원을 하나도 부르지 않는다")
    void loadsNoExternalResources() throws Exception {
        String body = authorize(REJECTED, "text/html").andReturn()
                .getResponse().getContentAsString();

        assertThat(body).doesNotContain("http://").doesNotContain("https://");
    }

    /** 허용된 주소는 이 변경과 무관하게 302 여야 한다. */
    @Test
    @DisplayName("허용된 주소는 브라우저 요청이어도 302 그대로")
    void allowedRedirectStillRedirects() throws Exception {
        authorize("exp://192.168.0.10:8081/--/auth", "text/html")
                .andExpect(status().isFound());
    }

    private ResultActions authorize(String redirect, String accept) throws Exception {
        return mockMvc.perform(get("/api/auth/oauth/kakao/authorize")
                .param("redirect", redirect)
                .header(HttpHeaders.ACCEPT, accept));
    }
}
