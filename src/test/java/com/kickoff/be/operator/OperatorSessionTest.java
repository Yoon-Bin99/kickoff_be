package com.kickoff.be.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.user.entity.User;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 운영자 인증 (계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>비밀번호만으로 열리지 않게 한다.</b> 운영자 화면은 이용자의 문의와 신고된 대화를
 * 보는 곳이고, 안전성 확보조치 기준 제6조 제2항이 외부 접속에 일회용 비밀번호 같은
 * 안전한 인증수단을 요구한다.
 *
 * 검사 순서는 인증(401) → 동의(403) → 운영자 계정인지(403) → 운영자 세션(401)이다.
 * 마지막이 <b>403 이 아니라 401</b> 인 것이 중요하다 — FE 는 그 코드를 보고 OTP 입력
 * 화면을 띄운다.
 */
class OperatorSessionTest extends IntegrationTestSupport {

    private User operator;
    private User normal;

    @BeforeEach
    void setUpAccounts() {
        operator = createUser("operator@kickoff.test", "운영자", "010-9999-9999");
        normal = createUser("user@example.com", "김일반", "010-1111-1111");
    }

    @Test
    @DisplayName("맞는 OTP 로 세션이 열린다")
    void openingSessionSucceeds() throws Exception {
        openSession(operator, currentOtp())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("운영자가 아니면 403 이다 — OTP 를 보기도 전에")
    void nonOperatorIsForbidden() throws Exception {
        openSession(normal, currentOtp())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("틀린 OTP 는 401 OPERATOR_OTP_INVALID 다")
    void wrongOtpIsRejected() throws Exception {
        openSession(operator, "000000")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_OTP_INVALID"));
    }

    /** 어깨너머로 본 코드가 그 30초 안에 다시 쓰이는 것을 막는다 (계약서 §7-2). */
    @Test
    @DisplayName("같은 OTP 를 다시 쓰면 거절된다")
    void otpCannotBeReused() throws Exception {
        String otp = currentOtp();
        openSession(operator, otp).andExpect(status().isOk());

        openSession(operator, otp)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_OTP_INVALID"));
    }

    @Test
    @DisplayName("연속 5번 틀리면 15분 잠긴다")
    void fiveFailuresLock() throws Exception {
        for (int i = 0; i < 5; i++) {
            openSession(operator, "000000").andExpect(status().isUnauthorized());
        }

        openSession(operator, currentOtp())
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OPERATOR_OTP_LOCKED"));
    }

    // ── 운영자 API 에 거는 검사 (계약서 §7-1 머리말·§7-2)

    @Test
    @DisplayName("세션 없이 문의함을 열면 401 OPERATOR_SESSION_REQUIRED 다")
    void supportInboxNeedsSession() throws Exception {
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_SESSION_REQUIRED"));
    }

    @Test
    @DisplayName("세션을 열면 문의함이 열린다")
    void supportInboxOpensWithSession() throws Exception {
        String token = token(openSession(operator, currentOtp()));

        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, token))
                .andExpect(status().isOk());
    }

    /**
     * 운영자가 아닌 계정은 <b>세션 검사 전에</b> 403 이다 — 순서가 뒤집히면 일반
     * 사용자가 401 을 받고 FE 가 OTP 입력 화면을 띄운다.
     */
    @Test
    @DisplayName("운영자가 아니면 문의함은 403 이다 — 401 이 아니다")
    void nonOperatorGetsForbiddenNotUnauthorized() throws Exception {
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(normal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** 토큰만 맞으면 되게 두면, 계정이 바뀌어도 같은 열쇠가 계속 듣는다. */
    @Test
    @DisplayName("남의 운영자 토큰으로는 통과하지 못한다")
    void tokenIsBoundToTheOperator() throws Exception {
        String token = token(openSession(operator, currentOtp()));

        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(normal))
                        .header(OperatorGuard.HEADER, token))
                .andExpect(status().isForbidden());
    }

    // ── 감사 기록 (계약서 §7-2 「감사 기록」)

    @Test
    @DisplayName("운영자 API 호출마다 접속기록이 남는다")
    void everyCallIsAudited() throws Exception {
        String token = token(openSession(operator, currentOtp()));
        mockMvc.perform(get("/api/support/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, token))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("운영자 세션 발급", "문의방 목록");
        assertThat(auditLogRepository.findAll())
                .allSatisfy(log -> assertThat(log.getOperatorUserId())
                        .isEqualTo(operator.getId()));
    }

    /** 잠금이 왜 걸렸는지 알 수 있어야 한다 — 실패도 접근의 기록이다. */
    @Test
    @DisplayName("세션 발급 실패도 기록된다")
    void failedAttemptsAreAudited() throws Exception {
        openSession(operator, "000000").andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getAction())
                .contains("운영자 세션 실패");
    }

    /** 접속기록에는 내용이 들어가지 않는다 (계약서 §7-2) — 그 자체가 개인정보 더미가 된다. */
    @Test
    @DisplayName("접속기록에는 대상 식별값만 남고 내용은 없다")
    void auditKeepsNoContent() throws Exception {
        String token = token(openSession(operator, currentOtp()));
        mockMvc.perform(get("/api/support/rooms/{userId}/chat", normal.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(operator))
                        .header(OperatorGuard.HEADER, token))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getAction()).isEqualTo("문의방 열람");
                    assertThat(log.getTargetUserId()).isEqualTo(normal.getId());
                });
    }

    // ── 헬퍼

    private ResultActions openSession(User user, String otp) throws Exception {
        return mockMvc.perform(post("/api/admin/session")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"%s\"}".formatted(otp)));
    }

    private String token(ResultActions actions) throws Exception {
        return JsonPath.read(bodyOf(actions), "$.operatorToken");
    }

    /** 테스트 설정의 비밀키로 지금 유효한 코드를 만든다 (인증 앱이 하는 일). */
    private String currentOtp() {
        return totpVerifier.currentCode(operatorTotpProperties.secret(), Instant.now());
    }
}
