package com.kickoff.be.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 로그 마스킹. 여기서 지키려는 것은 <b>원문이 절대 새지 않는다</b>는 한 가지다.
 * 읽기 좋은 모양은 그다음이라, 형식이 애매한 입력은 전부 통째로 가리는 쪽으로 판정한다.
 */
class LogMaskTest {

    @Test
    @DisplayName("이메일은 첫 글자와 도메인만 남는다")
    void email() {
        assertThat(LogMask.email("kim@example.com")).isEqualTo("k***@example.com");
        assertThat(LogMask.email("a@b.co")).isEqualTo("a***@b.co");
    }

    @Test
    @DisplayName("이메일 형식이 아니면 통째로 가린다 — 원문을 흘리느니 못 읽는 편이 낫다")
    void emailNotAnAddress() {
        assertThat(LogMask.email("kim.example.com")).isEqualTo("?");
        assertThat(LogMask.email("@example.com")).isEqualTo("?");
        assertThat(LogMask.email("")).isEqualTo("?");
        assertThat(LogMask.email(null)).isEqualTo("?");
    }

    @Test
    @DisplayName("전화번호는 뒤 4자리만 남는다")
    void phone() {
        assertThat(LogMask.phone("010-1234-5678")).isEqualTo("***-****-5678");
        assertThat(LogMask.phone("01012345678")).isEqualTo("***-****-5678");
    }

    @Test
    @DisplayName("전화번호가 너무 짧거나 없으면 통째로 가린다")
    void phoneTooShort() {
        assertThat(LogMask.phone("123")).isEqualTo("?");
        assertThat(LogMask.phone(null)).isEqualTo("?");
    }

    /**
     * 마스킹의 목적 자체를 지키는 단언이다. 위 테스트들은 "모양"을 보지만 이건
     * "원문이 결과에 남지 않는가"를 본다 — 구현을 바꾸다 로컬파트를 더 남기게 되면
     * 모양 테스트는 고쳐 쓰면서 통과시킬 수 있어도 이건 못 넘어간다.
     */
    @Test
    @DisplayName("결과에 원문이 그대로 담기지 않는다")
    void neverContainsOriginal() {
        assertThat(LogMask.email("hongildong@example.com")).doesNotContain("hongildong");
        assertThat(LogMask.phone("010-0000-0001")).doesNotContain("0000-");
    }
}
