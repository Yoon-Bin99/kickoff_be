package com.kickoff.be.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.dto.FaqItem;
import com.kickoff.be.support.service.SupportKnowledge;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * 고객센터 지식 문서 (계약서 §7-1, v1.22.0).
 *
 * 이 문서는 <b>사람이 고치고 코드가 읽는다</b>. 그래서 깨지는 방식이 조용하다 — 제목
 * 형식을 바꾸면 FAQ 가 0개가 되고, 지식 섹션 제목을 고치면 AI 가 서비스를 모르는 채로
 * 답한다. 둘 다 200 이고 로그도 없다.
 *
 * 문서를 고치는 사람은 파서 사정을 모른다. 그래서 여기서 문서를 직접 훑는다.
 */
class SupportKnowledgeTest extends IntegrationTestSupport {

    @Autowired
    private SupportKnowledge knowledge;

    @Test
    @DisplayName("FAQ 가 문서에서 읽힌다 — 하나도 못 읽으면 퀵버튼이 통째로 사라진다")
    void faqIsParsed() {
        List<FaqItem> faq = knowledge.faq();

        assertThat(faq).isNotEmpty();
        assertThat(faq).allSatisfy(item -> {
            assertThat(item.id()).isPositive();
            assertThat(item.question()).isNotBlank();
            // 답변이 비면 버튼을 눌러도 아무 말도 안 나온다. 질문만 있는 FAQ 는 없는 것만 못하다.
            assertThat(item.answer()).isNotBlank();
        });
    }

    @Test
    @DisplayName("FAQ id 가 겹치지 않는다 — FE 가 이걸 키로 쓴다")
    void faqIdsAreUnique() {
        List<Integer> ids = knowledge.faq().stream().map(FaqItem::id).toList();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AI 지식이 실제로 읽힌다 — 비면 AI 가 서비스를 모르는 채로 답한다")
    void aiKnowledgeIsParsed() {
        String text = knowledge.aiKnowledge();

        assertThat(text).isNotBlank();
        // 하드 제약이 빠지면 AI 가 환불·제재를 약속할 수 있다 (계약서 §7-1)
        assertThat(text).contains("환불");
        assertThat(text).contains("킥오프");
    }

    @Test
    @DisplayName("FAQ 구역과 AI 구역이 섞이지 않는다")
    void sectionsDoNotBleed() {
        // AI 지식 쪽에도 '###' 제목이 있다. 파서가 거기까지 훑으면 "역할과 어조" 같은
        // 소제목이 FAQ 질문으로 화면에 뜬다 — 에러 없이, 그냥 이상한 버튼이 생긴다.
        assertThat(knowledge.faq()).noneSatisfy(item ->
                assertThat(item.question()).contains("역할과 어조"));
        assertThat(knowledge.faq()).noneSatisfy(item ->
                assertThat(item.question()).contains("하드 제약"));
    }

    @Test
    @DisplayName("문서가 파서가 기대하는 구조를 지키고 있다")
    void documentKeepsExpectedStructure() {
        // 여기가 깨지면 문서를 고친 사람에게 "무엇이 달라졌는지"를 알려 줘야 한다.
        // 파서를 고치든 문서를 되돌리든, 그냥 두면 화면에서만 조용히 비어 버린다.
        String md = read();
        assertThat(md).as("FAQ 구역 제목").contains("## FAQ");
        assertThat(md).as("AI 지식 구역 제목").contains("## AI 상담사 시스템 프롬프트용 지식");
        assertThat(md).as("'### 1.' 형식의 질문").containsPattern("(?m)^###\\s+\\d+\\.\\s+\\S");
    }

    @Test
    @DisplayName("빌드가 docs/ 의 지식 문서를 리소스로 실어 준다")
    void documentIsOnClasspath() {
        // v1.21.0 에서 이 검사가 없어 배포 이미지에 문서가 안 실렸고, 운영에서만 500 이
        // 났다. 이제 빌드가 먼저 멈추지만, 클래스패스에서 실제로 읽히는지도 함께 본다.
        assertThat(read()).contains("킥오프 고객센터");
    }

    private String read() {
        try (InputStream in =
                     new ClassPathResource("support/support-knowledge.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("support/support-knowledge.md 를 못 찾았다", e);
        }
    }
}
