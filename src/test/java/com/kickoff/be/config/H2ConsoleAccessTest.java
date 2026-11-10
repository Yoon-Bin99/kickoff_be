package com.kickoff.be.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * H2 콘솔 경로가 개발 밖에서는 막혀 있는지.
 *
 * <b>왜 테스트가 필요한가.</b> 운영에서 콘솔이 떠 있던 것은 아니다 —
 * {@code spring.h2.console.enabled} 가 dev 블록에만 true 라 서블릿이 등록되지 않는다.
 * 그런데 막고 있는 것이 <b>프로퍼티 한 줄뿐</b>이고 H2 와 h2console 모듈은 운영 이미지에
 * 그대로 들어간다. 그 한 줄이 실수로 켜지는 날, 시큐리티 규칙까지 열려 있으면 DB 콘솔이
 * 그대로 공개된다. 이 테스트가 고정하는 것은 프로퍼티가 아니라 <b>시큐리티 규칙</b>이다.
 *
 * 테스트는 {@code test} 프로파일로 돈다(= dev 가 아니다). 그래서 운영과 같은 가지를 타고,
 * DB 를 PostgreSQL 로 띄우지 않아도 검증된다.
 *
 * <h2>어느 단정이 실제로 무엇을 증명하는가</h2>
 * <b>{@link #authenticatedGets403} 하나가 이 테스트의 핵심이다.</b> 나머지는 "막혀 있다"만
 * 보여 준다.
 *
 * 익명 요청은 거부되면 <b>401</b> 이 된다 — 스프링 시큐리티가 "권한 없음"을 만나도 인증이
 * 익명이면 인증을 요구하는 쪽으로 돌린다. 그런데 규칙을 <b>아예 빼도</b> 익명 요청은
 * {@code anyRequest().authenticated()} 에 걸려 똑같이 401 이다. 그래서 익명 401 만으로는
 * {@code denyAll} 이 걸려 있는지 알 수 없다.
 *
 * 토큰이 있을 때 갈린다. 규칙이 없으면 인증을 통과해 <b>404</b>(서블릿 없음)가 되고,
 * {@code denyAll} 이면 <b>403</b> 이다. 그 둘의 차이가 "로그인한 사람에게도 닫혀 있다"는
 * 증거다.
 */
class H2ConsoleAccessTest extends IntegrationTestSupport {

    /**
     * <b>이 테스트가 규칙의 존재를 증명한다.</b> 403 이 404 로 바뀌면 {@code denyAll} 이
     * 빠졌다는 뜻이다 — 그 상태는 "토큰만 있으면 DB 콘솔에 닿는다"를 뜻한다. 콘솔은
     * 로그인한 사용자에게도 열려서는 안 되는 경로다.
     */
    @Test
    @DisplayName("토큰이 있어도 H2 콘솔은 403 — 404 가 되면 규칙이 빠진 것")
    void authenticatedGets403() throws Exception {
        User user = createUser("h2@example.com", "김콘솔", "010-0000-0009");

        mockMvc.perform(get("/h2-console/login.do")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/h2-console")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    /** 비로그인은 401 이다(403 이 아니다 — 위 클래스 주석 참고). 어느 쪽이든 닿지 못한다. */
    @Test
    @DisplayName("비로그인은 401 — 콘솔에 닿지 못한다")
    void anonymousIsBlocked() throws Exception {
        mockMvc.perform(get("/h2-console")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/h2-console/login.do")).andExpect(status().isUnauthorized());
    }

    /**
     * 프리플라이트로 우회되지 않는지.
     *
     * {@code OPTIONS /**} 가 permitAll 이라, H2 규칙이 그 뒤에 놓이면 콘솔 경로의 OPTIONS 가
     * <b>통과해 200</b> 이 된다. 규칙 순서를 맨 앞으로 둔 것이 그래서이고, 이 테스트가 그
     * 순서를 고정한다. 200 이 되는 날 순서가 뒤집혔다는 뜻이다.
     */
    @Test
    @DisplayName("프리플라이트로도 통과하지 않는다 — 200 이 되면 규칙 순서가 뒤집힌 것")
    void preflightIsBlocked() throws Exception {
        mockMvc.perform(options("/h2-console/login.do"))
                .andExpect(status().isUnauthorized());
    }
}
