package com.kickoff.be.legal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.legal.service.MarkdownToHtml;
import com.kickoff.be.support.IntegrationTestSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 약관·개인정보처리방침 페이지 (계약서 §3, v1.21.0).
 *
 * 이 기능이 조용히 망가지는 방식은 둘이다.
 *
 * 하나, <b>변환기가 모르는 구문이 문서에 들어오는 것.</b> 서버는 200 을 내고 로그도 조용한데
 * 화면에는 마크다운 기호가 날것으로 찍힌다. 그래서 문서를 직접 훑어 지원 범위 밖 구문이
 * 있으면 여기서 깨뜨린다 — 문서를 고치는 사람은 변환기 사정을 모른다.
 *
 * 둘, <b>배포에서 파일이 빠지는 것.</b> 원본은 docs/ 에 있고 빌드가 리소스로 복사한다.
 * 그 설정이 사라지면 로컬에서는 멀쩡하고 배포에서만 500 이 난다.
 */
class LegalDocumentTest extends IntegrationTestSupport {

    // ── 서빙

    @Test
    @DisplayName("약관·방침은 로그인 없이 열린다 — 가입 전에 읽는 문서다")
    void servedWithoutAuth() throws Exception {
        for (String path : new String[]{"/terms", "/privacy"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("text/html"));
        }
    }

    @Test
    @DisplayName("HEAD 도 열린다 — 링크 미리보기가 HEAD 로 먼저 찔러 본다")
    void headIsAllowed() throws Exception {
        // share-card 에서 실제로 겪었다. 시큐리티에서 GET 만 열면 HEAD 가
        // anyRequest().authenticated() 로 떨어져 401 이 되는데, 우리 로그에는 401 한 줄만
        // 남아 원인을 찾기 어렵다.
        mockMvc.perform(head("/terms")).andExpect(status().isOk());
        mockMvc.perform(head("/privacy")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("문서 내용이 실제로 실려 나온다 — 빈 껍데기가 아니다")
    void containsDocumentBody() throws Exception {
        String terms = mockMvc.perform(get("/terms")).andReturn()
                .getResponse().getContentAsString();
        assertThat(terms).contains("<h1>", "이용약관", "제1조");

        String privacy = mockMvc.perform(get("/privacy")).andReturn()
                .getResponse().getContentAsString();
        assertThat(privacy).contains("<h1>", "개인정보처리방침", "<table>");
    }

    @Test
    @DisplayName("모바일에서 읽을 수 있게 viewport 가 있다")
    void hasViewport() throws Exception {
        assertThat(mockMvc.perform(get("/terms")).andReturn().getResponse().getContentAsString())
                .contains("name=\"viewport\"");
    }

    // ── 문서가 변환기의 지원 범위 안에 있는가

    @Test
    @DisplayName("문서에 변환기가 모르는 마크다운 구문이 없다")
    void documentsUseOnlySupportedSyntax() {
        // 여기가 깨지면 변환기를 고치거나 문서에서 그 구문을 빼야 한다. 그냥 두면
        // 화면에 기호가 날것으로 찍힌다 — 에러 없이.
        record Unsupported(String name, Pattern pattern) { }
        List<Unsupported> checks = List.of(
                new Unsupported("링크 [텍스트](주소)", Pattern.compile("\\[[^\\]]*\\]\\([^)]*\\)")),
                new Unsupported("이미지 ![...]", Pattern.compile("!\\[")),
                new Unsupported("코드블록 ```", Pattern.compile("^```", Pattern.MULTILINE)),
                new Unsupported("인라인 코드 `", Pattern.compile("`")),
                new Unsupported("인용 >", Pattern.compile("^>", Pattern.MULTILINE)),
                new Unsupported("h3 이상 ###", Pattern.compile("^#{3,} ", Pattern.MULTILINE)),
                // 들여쓰기는 공백이나 탭이지 줄바꿈이 아니다. 여기에 \s 를 쓰면 CRLF 문서에서
                // 빈 줄의 \r\n 두 글자를 들여쓰기로 먹고, 뒤따르는 최상위 목록을 "들여쓴 항목"
                // 으로 오탐한다. .gitattributes 로 LF 를 고정해 두었지만 검사식 자체도 막아 둔다.
                new Unsupported("중첩 목록(들여쓴 항목)",
                        Pattern.compile("^(?:[ ]{2,}|\\t)[-*] ", Pattern.MULTILINE)),
                new Unsupported("수평선 ---",
                        Pattern.compile("^-{3,}\\s*$", Pattern.MULTILINE)));

        List<String> found = new ArrayList<>();
        for (String file : new String[]{"terms-of-service.md", "privacy-policy.md"}) {
            String md = read(file);
            for (Unsupported check : checks) {
                if (check.pattern().matcher(md).find()) {
                    found.add(file + " 에 " + check.name());
                }
            }
        }
        assertThat(found)
                .as("변환기가 못 다루는 구문 — MarkdownToHtml 을 고치거나 문서에서 뺄 것")
                .isEmpty();
    }

    @Test
    @DisplayName("빌드가 docs/ 의 문서를 리소스로 실어 준다")
    void documentsAreOnClasspath() {
        // build.gradle 의 processResources 가 복사한다. 그 설정이 사라지면 로컬 docs/ 는
        // 그대로라 아무도 모르다가 배포에서만 500 이 난다.
        assertThat(read("terms-of-service.md")).contains("이용약관");
        assertThat(read("privacy-policy.md")).contains("개인정보처리방침");
    }

    @Test
    @DisplayName("채우지 않은 플레이스홀더가 없다 — 문의처는 법적으로 필요한 항목이다")
    void noUnfilledPlaceholders() {
        // 원래 `[운영자 이메일 주소]` 같은 자리가 있었고 사용자 확인 후 채웠다. 이제 위험은
        // 반대 방향이다 — 문서를 손볼 때 새 자리를 비워 둔 채 배포하는 것. 그러면 약관
        // 화면에 대괄호가 그대로 보이는데, 서버는 200 이고 아무 로그도 안 남는다.
        for (String file : new String[]{"terms-of-service.md", "privacy-policy.md"}) {
            assertThat(Pattern.compile("\\[[^\\]]+\\]").matcher(read(file)).results().toList())
                    .as(file + " 에 채우지 않은 자리")
                    .isEmpty();
            // 문의처가 통째로 빠지는 것도 같은 종류의 조용한 사고다
            assertThat(read(file)).as(file + " 의 문의처").contains("@");
        }
    }

    // ── 변환기

    @Test
    @DisplayName("제목·문단·목록·표·굵게를 옮긴다")
    void rendersSupportedSyntax() {
        String html = MarkdownToHtml.render("""
                # 제목
                ## 절

                문단 첫 줄
                이어지는 줄

                1. 하나
                2. 둘

                - 불릿

                | 구분 | 항목 |
                |---|---|
                | 필수 | 이메일 |

                **굵게** 포함 문단
                """);

        assertThat(html).contains("<h1>제목</h1>", "<h2>절</h2>");
        // 원본이 가독성 때문에 나눈 줄은 한 문단이다. 줄마다 <p> 를 만들면 토막 나 보인다.
        assertThat(html).contains("<p>문단 첫 줄 이어지는 줄</p>");
        assertThat(html).contains("<ol>", "<li>하나</li>", "<li>둘</li>", "</ol>");
        assertThat(html).contains("<ul>", "<li>불릿</li>", "</ul>");
        assertThat(html).contains("<th>구분</th>", "<td>필수</td>", "<td>이메일</td>");
        assertThat(html).contains("<strong>굵게</strong>");
    }

    /**
     * <b>실제 약관에서 잘못 렌더되던 모양이다.</b> 폰 폭(375)에서 화면을 들여다보다 찾았다 —
     * 서버는 200 이고 로그도 조용해서 그 전까지 아무도 몰랐다.
     *
     * 항목의 이어지는 줄을 목록 밖으로 흘리면 두 가지가 한꺼번에 어긋난다. 그 줄이
     * 들여쓰기를 잃고 문단으로 떨어지고, 목록이 거기서 닫히는 바람에 <b>다음 항목이 새
     * 목록으로 시작해 번호가 1 로 되돌아간다.</b>
     *
     * 약관에서 번호는 조항 번호다. "제2조 1., 2." 가 "1., 1." 로 보이면 조문을 가리켜
     * 이야기할 수 없게 된다 — 법적 문서에서는 서식이 아니라 내용이 틀리는 것에 가깝다.
     */
    @Test
    @DisplayName("들여쓴 이어쓰기 줄은 그 항목에 붙는다 — 번호가 1 로 되돌아가면 안 된다")
    void continuationLinesStayInTheItem() {
        String html = MarkdownToHtml.render("""
                1. 첫 항목이고
                   이어지는 줄이 있다
                2. 둘째 항목
                """);

        assertThat(html).contains("<li>첫 항목이고 이어지는 줄이 있다</li>");
        assertThat(html).contains("<li>둘째 항목</li>");
        assertThat(html)
                .as("목록이 중간에 닫히면 <ol> 이 두 번 열리고 번호가 1 로 되돌아간다")
                .containsOnlyOnce("<ol>");
        assertThat(html)
                .as("이어지는 줄이 목록 밖 문단으로 떨어지면 안 된다")
                .doesNotContain("<p>이어지는 줄이 있다</p>");
    }

    /** 불릿도 같은 규칙이다 — 한 문서 안에서 목록 종류에 따라 달라지면 안 된다. */
    @Test
    @DisplayName("불릿 목록의 이어쓰기 줄도 항목에 붙는다")
    void continuationLinesWorkForBullets() {
        String html = MarkdownToHtml.render("""
                - 첫 불릿이고
                  이어지는 줄
                - 둘째 불릿
                """);

        assertThat(html).contains("<li>첫 불릿이고 이어지는 줄</li>", "<li>둘째 불릿</li>");
        assertThat(html).containsOnlyOnce("<ul>");
    }

    /**
     * 들여쓰지 않은 줄은 목록의 끝이다. 넓게 잡으면 목록 뒤 문단이 마지막 항목 안으로
     * 빨려 들어가는데, 그건 원문과 다른 문서를 보여주는 것이다.
     */
    @Test
    @DisplayName("들여쓰지 않은 줄에서는 목록이 끝난다")
    void unindentedLineEndsTheList() {
        String html = MarkdownToHtml.render("""
                1. 항목
                목록 뒤 문단
                """);

        assertThat(html).contains("<li>항목</li>");
        assertThat(html).contains("<p>목록 뒤 문단</p>");
    }

    @Test
    @DisplayName("HTML 특수문자를 이스케이프한다")
    void escapesHtml() {
        // 지금 문서에는 없지만, 들어오는 날 조용히 태그로 해석되면 화면이 깨진다
        assertThat(MarkdownToHtml.render("a < b & c"))
                .contains("a &lt; b &amp; c")
                .doesNotContain("a < b & c");
    }

    @Test
    @DisplayName("구분선 없는 | 줄은 표가 아니라 문단이다")
    void pipeWithoutDividerIsNotTable() {
        // 마크다운에서도 표가 아니다. 억지로 표를 만들면 원본을 뷰어로 볼 때와 화면이 갈린다.
        String html = MarkdownToHtml.render("| 그냥 파이프가 있는 문장 |");
        assertThat(html).doesNotContain("<table>");
        assertThat(html).contains("<p>");
    }

    private String read(String fileName) {
        try (InputStream in = new ClassPathResource("legal/" + fileName).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("legal/" + fileName + " 을 클래스패스에서 못 찾았다", e);
        }
    }
}
