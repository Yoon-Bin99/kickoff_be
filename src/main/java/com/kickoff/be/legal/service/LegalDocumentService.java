package com.kickoff.be.legal.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 약관·개인정보처리방침 페이지 (계약서 §3, v1.21.0).
 *
 * 원본 마크다운은 {@code docs/} 에 있고 빌드가 {@code legal/} 로 복사해 jar 에 싣는다
 * (build.gradle 의 processResources). 복사본을 저장소에 두지 않으므로 어긋날 자리가 없다.
 *
 * 문서가 바뀌지 않으니 변환 결과를 캐시한다. 다만 <b>기동 시점에 미리 읽지는 않는다</b> —
 * 파일이 없으면 그때 서버가 통째로 안 뜨는데, 약관 페이지 하나 때문에 매칭도 로그인도
 * 막을 이유가 없다. 없으면 그 페이지만 500 이 난다.
 */
@Service
public class LegalDocumentService {

    /** 화면 폭이 좁은 기기에서 읽는 문서다. 시스템 글꼴을 쓰고 본문 폭만 제한한다. */
    private static final String PAGE = """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
              :root { color-scheme: light dark; }
              body {
                font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo",
                             "Noto Sans KR", "Malgun Gothic", sans-serif;
                line-height: 1.7; margin: 0 auto; padding: 24px 20px 64px;
                max-width: 720px; word-break: keep-all; overflow-wrap: break-word;
              }
              h1 { font-size: 1.5rem; line-height: 1.4; margin: 0 0 24px; }
              h2 { font-size: 1.125rem; line-height: 1.5; margin: 32px 0 8px; }
              p, li { font-size: 0.95rem; }
              ul, ol { padding-left: 22px; }
              li { margin: 4px 0; }
              strong { font-weight: 600; }
              /* 표는 좁은 화면에서 본문을 밀어내지 않고 표 안에서만 가로 스크롤한다. */
              .table-wrap { overflow-x: auto; margin: 12px 0; }
              table { border-collapse: collapse; min-width: 100%%; font-size: 0.9rem; }
              th, td { border: 1px solid #8883; padding: 8px 10px; text-align: left;
                       vertical-align: top; }
              th { font-weight: 600; white-space: nowrap; }
            </style>
            </head>
            <body>
            %s
            </body>
            </html>
            """;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String terms() {
        return page("terms-of-service.md", "킥오프 이용약관");
    }

    public String privacy() {
        return page("privacy-policy.md", "킥오프 개인정보처리방침");
    }

    private String page(String fileName, String title) {
        return cache.computeIfAbsent(fileName, name -> {
            String html = MarkdownToHtml.render(read(name))
                    // 표만 스크롤 컨테이너로 감싼다. 렌더러가 HTML 구조만 알고 화면 사정은
                    // 모르게 두려고 여기서 씌운다.
                    .replace("<table>", "<div class=\"table-wrap\"><table>")
                    .replace("</table>", "</table></div>");
            return PAGE.formatted(title, html);
        });
    }

    private String read(String fileName) {
        ClassPathResource resource = new ClassPathResource("legal/" + fileName);
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 빌드가 docs/ 에서 복사해 넣는 파일이다. 없다면 build.gradle 의
            // processResources 설정이 사라진 것이다 — 원인을 메시지에 적어 둔다.
            throw new IllegalStateException(
                    "약관 문서를 찾을 수 없습니다: legal/" + fileName
                            + " — build.gradle 의 processResources 가 docs/ 에서 복사한다.", e);
        }
    }
}
