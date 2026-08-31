package com.kickoff.be.legal.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 약관·개인정보처리방침을 HTML 로 옮긴다 (계약서 §3, v1.21.0).
 *
 * <b>범용 마크다운 변환기가 아니다.</b> 저 두 문서가 실제로 쓰는 여섯 가지만 다룬다 —
 * 제목(h1·h2), 문단, 번호 목록, 불릿 목록, 표, 굵게. 라이브러리를 들이지 않은 이유는
 * 기술 스택이 확정돼 있고, 문서 두 장을 위해 의존성을 늘릴 값어치가 없어서다.
 *
 * <b>대신 지원 범위를 테스트가 지킨다.</b> 문서에 링크·코드블록·인용처럼 여기서 못 다루는
 * 구문이 새로 들어오면 {@code LegalDocumentTest} 가 먼저 깨진다. 그게 없으면 어떻게 되냐면,
 * 새 구문이 <b>날것 그대로 화면에 찍힌다</b> — 서버는 200 을 내고 로그도 조용하다. 약관
 * 페이지에 {@code [문의](mailto:...)} 가 그대로 보이는데 아무도 모르는 상태가 된다.
 *
 * 입력은 우리가 쓴 문서지만 이스케이프는 그대로 한다. 문서에 부등호가 들어오는 날
 * 조용히 태그로 해석되는 걸 막는 비용이 거의 없다.
 */
public final class MarkdownToHtml {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ORDERED = Pattern.compile("^(\\d+)\\. (.*)$");
    private static final Pattern TABLE_DIVIDER = Pattern.compile("^\\|[\\s|:-]+\\|$");

    private MarkdownToHtml() {
    }

    public static String render(String markdown) {
        List<String> out = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);

        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.strip();

            if (trimmed.isEmpty()) {
                i++;
            } else if (trimmed.startsWith("## ")) {
                out.add("<h2>" + inline(trimmed.substring(3)) + "</h2>");
                i++;
            } else if (trimmed.startsWith("# ")) {
                out.add("<h1>" + inline(trimmed.substring(2)) + "</h1>");
                i++;
            } else if (trimmed.startsWith("|")) {
                i = table(lines, i, out);
            } else if (ORDERED.matcher(trimmed).matches()) {
                i = list(lines, i, out, true);
            } else if (trimmed.startsWith("- ")) {
                i = list(lines, i, out, false);
            } else {
                i = paragraph(lines, i, out);
            }
        }
        return String.join("\n", out);
    }

    /**
     * 표. 첫 줄이 머리글, 둘째 줄이 {@code |---|---|} 구분선이다.
     *
     * 구분선이 없으면 표로 보지 않고 문단으로 되돌린다 — 마크다운에서도 그게 표가 아니라서,
     * 여기서 억지로 표를 만들면 원본을 마크다운 뷰어로 볼 때와 화면이 달라진다.
     */
    private static int table(String[] lines, int start, List<String> out) {
        if (start + 1 >= lines.length || !TABLE_DIVIDER.matcher(lines[start + 1].strip()).matches()) {
            return paragraph(lines, start, out);
        }
        out.add("<table>");
        out.add("<thead>" + row(lines[start], "th") + "</thead>");
        out.add("<tbody>");
        int i = start + 2;
        while (i < lines.length && lines[i].strip().startsWith("|")) {
            out.add(row(lines[i], "td"));
            i++;
        }
        out.add("</tbody>");
        out.add("</table>");
        return i;
    }

    private static String row(String line, String cellTag) {
        String body = line.strip();
        body = body.substring(1, body.length() - (body.endsWith("|") ? 1 : 0));
        StringBuilder sb = new StringBuilder("<tr>");
        for (String cell : body.split("\\|", -1)) {
            sb.append("<").append(cellTag).append(">")
              .append(inline(cell.strip()))
              .append("</").append(cellTag).append(">");
        }
        return sb.append("</tr>").toString();
    }

    private static int list(String[] lines, int start, List<String> out, boolean ordered) {
        String tag = ordered ? "ol" : "ul";
        out.add("<" + tag + ">");
        int i = start;
        while (i < lines.length) {
            String t = lines[i].strip();
            String item;
            if (ordered) {
                Matcher m = ORDERED.matcher(t);
                if (!m.matches()) {
                    break;
                }
                item = m.group(2);
            } else if (t.startsWith("- ")) {
                item = t.substring(2);
            } else {
                break;
            }
            out.add("<li>" + inline(item) + "</li>");
            i++;
        }
        out.add("</" + tag + ">");
        return i;
    }

    /**
     * 문단. 빈 줄이 나올 때까지 이어 붙인다 — 원본이 가독성 때문에 줄을 나눠 놨을 뿐
     * 한 문단이기 때문이다. 줄마다 {@code <p>} 를 만들면 문장이 토막 나 보인다.
     */
    private static int paragraph(String[] lines, int start, List<String> out) {
        StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < lines.length) {
            String t = lines[i].strip();
            if (t.isEmpty() || t.startsWith("#") || t.startsWith("|") || t.startsWith("- ")
                    || ORDERED.matcher(t).matches()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(t);
            i++;
        }
        out.add("<p>" + inline(sb.toString()) + "</p>");
        // 첫 줄이 이미 다른 블록의 시작이면 여기서 한 줄도 안 먹고 무한 반복이 된다.
        return i == start ? start + 1 : i;
    }

    private static String inline(String text) {
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
        return BOLD.matcher(escaped).replaceAll("<strong>$1</strong>");
    }
}
