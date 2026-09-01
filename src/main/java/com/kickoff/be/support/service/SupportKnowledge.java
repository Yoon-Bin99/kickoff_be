package com.kickoff.be.support.service;

import com.kickoff.be.support.dto.FaqItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 고객센터 지식 문서 (계약서 §7-1, v1.22.0).
 *
 * 원본은 {@code docs/support-knowledge.md} 한 벌이고 빌드가 리소스로 복사한다
 * (build.gradle 의 processResources). 약관 문서와 같은 방식이고, 복사가 안 되면
 * <b>빌드가 멈춘다</b> — v1.21.0 에서 그 검사가 없어 운영에서만 500 이 난 적이 있다.
 *
 * 문서 한 장이 두 곳으로 갈린다.
 * <ul>
 *   <li>{@code ## FAQ} 아래 {@code ### N. 질문} 들 → 퀵버튼 (서버 저장·AI 호출 없음)</li>
 *   <li>{@code ## AI 상담사 시스템 프롬프트용 지식} 아래 전부 → AI 시스템 프롬프트</li>
 * </ul>
 *
 * <b>파싱이 조용히 빈 결과를 내면 안 된다.</b> FAQ 가 0개면 화면에 퀵버튼이 통째로
 * 사라지고, 지식이 비면 AI 가 서비스를 모르는 채로 답한다 — 둘 다 200 이고 로그도
 * 조용하다. 그래서 기동 시점에 읽어 비어 있으면 시끄럽게 멈춘다.
 */
@Component
public class SupportKnowledge {

    private static final String RESOURCE = "support/support-knowledge.md";
    private static final String FAQ_HEADING = "## FAQ";
    private static final String AI_HEADING = "## AI 상담사 시스템 프롬프트용 지식";
    /** {@code ### 1. 매칭은 어떻게 하나요?} */
    private static final Pattern FAQ_ENTRY = Pattern.compile("^###\\s+(\\d+)\\.\\s+(.+)$");

    private final List<FaqItem> faq;
    private final String aiKnowledge;

    public SupportKnowledge() {
        String markdown = read();
        this.faq = List.copyOf(parseFaq(markdown));
        this.aiKnowledge = parseAiKnowledge(markdown);
        // 기동에서 멈추는 쪽을 택한다. 여기가 비면 FAQ 버튼이 사라지고 AI 가 서비스를
        // 모르는 채로 답하는데, 둘 다 에러 없이 그럴듯하게 동작해 한참 모른다.
        if (faq.isEmpty()) {
            throw new IllegalStateException(
                    RESOURCE + " 에서 FAQ 를 하나도 못 읽었습니다. "
                            + "'" + FAQ_HEADING + "' 아래 '### 1. 질문' 형식인지 확인하세요.");
        }
        if (aiKnowledge.isBlank()) {
            throw new IllegalStateException(
                    RESOURCE + " 에서 AI 지식을 못 읽었습니다. "
                            + "'" + AI_HEADING + "' 제목이 그대로인지 확인하세요.");
        }
    }

    public List<FaqItem> faq() {
        return faq;
    }

    /** AI 시스템 프롬프트에 붙는 서비스 지식. 원문 마크다운 그대로 넘긴다. */
    public String aiKnowledge() {
        return aiKnowledge;
    }

    /**
     * {@code ### N. 질문} 과 그 아래 답변을 짝지어 읽는다. 답변은 다음 제목이나 구분선
     * 전까지고, 줄바꿈은 공백으로 붙인다 — 원본이 가독성 때문에 나눈 줄이라 그대로 두면
     * 버튼 답변이 토막 나 보인다.
     */
    private static List<FaqItem> parseFaq(String markdown) {
        List<FaqItem> items = new ArrayList<>();
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);

        int i = indexOfHeading(lines, FAQ_HEADING);
        if (i < 0) {
            return items;
        }
        while (i < lines.length) {
            String line = lines[i].strip();
            // FAQ 구역이 끝나면 멈춘다. AI 지식 쪽 '###' 들을 질문으로 읽으면 안 된다.
            if (line.startsWith("## ") && !line.startsWith(FAQ_HEADING)) {
                break;
            }
            Matcher m = FAQ_ENTRY.matcher(line);
            if (!m.matches()) {
                i++;
                continue;
            }
            int id = Integer.parseInt(m.group(1));
            String question = m.group(2).strip();
            StringBuilder answer = new StringBuilder();
            i++;
            while (i < lines.length) {
                String body = lines[i].strip();
                if (body.startsWith("#") || body.equals("---")) {
                    break;
                }
                if (!body.isEmpty()) {
                    if (answer.length() > 0) {
                        answer.append(' ');
                    }
                    answer.append(body);
                }
                i++;
            }
            items.add(new FaqItem(id, question, answer.toString().strip()));
        }
        return items;
    }

    private static String parseAiKnowledge(String markdown) {
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        int start = indexOfHeading(lines, AI_HEADING);
        if (start < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < lines.length; i++) {
            sb.append(lines[i]).append('\n');
        }
        return sb.toString().strip();
    }

    private static int indexOfHeading(String[] lines, String heading) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().startsWith(heading)) {
                return i;
            }
        }
        return -1;
    }

    private static String read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "고객센터 지식 문서를 찾을 수 없습니다: " + RESOURCE
                            + " — build.gradle 의 processResources 가 docs/ 에서 복사한다.", e);
        }
    }
}
