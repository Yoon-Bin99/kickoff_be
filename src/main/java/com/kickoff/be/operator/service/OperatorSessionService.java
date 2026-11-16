package com.kickoff.be.operator.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.operator.dto.OperatorSessionResponse;
import com.kickoff.be.user.entity.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 운영자 세션 (계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>왜 비밀번호만으로 열지 않는가.</b> 운영자 화면은 이용자의 문의와 신고된 대화를 보는
 * 곳이다 — 개인정보 안전성 확보조치 기준 제6조 제2항이 외부 접속에 일회용 비밀번호 같은
 * 안전한 인증수단을 요구한다.
 *
 * <b>토큰은 메모리에만 둔다.</b> DB 에 넣으면 그 표 자체가 "운영자 권한 열쇠 모음"이 되고,
 * 재시작으로 끊기는 것은 오히려 바라는 바다(제6조 제4항의 자동 차단과 같은 방향).
 * 서버가 여러 대가 되면 그때는 공유 저장소가 필요하다 — 지금은 한 대다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorSessionService {

    /** 발급 후 30분, 연장 없음 (계약서 §7-2 — 일정 시간 뒤 자동 차단). */
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    /** 연속 5번 틀리면 15분 잠근다 (계약서 §7-2). */
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OperatorTotpProperties properties;
    private final TotpVerifier totpVerifier;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Failures> failures = new ConcurrentHashMap<>();

    /** 가장 최근에 쓴 시간 칸 — 같은 코드를 30초 안에 다시 받지 않는다 (계약서 §7-2). */
    private final Map<Long, Long> usedSteps = new ConcurrentHashMap<>();

    /**
     * OTP 를 확인하고 세션을 연다.
     *
     * <b>비밀키가 없으면 아무도 열 수 없다</b>(계약서 §7-2 — 운영에서 비면 운영자 API 전체가
     * 403). 닫힌 쪽으로 실패한다: 설정을 빠뜨린 배포가 운영자 화면을 열어 두지 않는다.
     */
    public OperatorSessionResponse open(User operator, String otp) {
        if (!properties.isConfigured()) {
            log.warn("OPERATOR_TOTP_SECRET 이 비어 있다 — 운영자 세션을 열 수 없다");
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        requireNotLocked(operator.getId());

        OptionalLong step = totpVerifier.verify(properties.secret(), otp, Instant.now());
        if (step.isEmpty()) {
            recordFailure(operator.getId());
            throw new BusinessException(ErrorCode.OPERATOR_OTP_INVALID);
        }
        // 한 번 쓴 코드는 같은 30초 안에 다시 받지 않는다 — 어깨너머로 본 코드가 그 창
        // 안에 재사용되는 것을 막는다.
        Long lastUsed = usedSteps.get(operator.getId());
        if (lastUsed != null && lastUsed == step.getAsLong()) {
            recordFailure(operator.getId());
            throw new BusinessException(ErrorCode.OPERATOR_OTP_INVALID);
        }
        usedSteps.put(operator.getId(), step.getAsLong());
        failures.remove(operator.getId());

        String token = newToken();
        // 밀리초까지만 내보낸다. 윈도우는 100ns 정밀도라 소수점이 7자리로 나가고,
        // 엄격한 파서를 쓰는 클라이언트가 걸린다 (ErrorResponse 와 같은 이유).
        OffsetDateTime expiresAt = OffsetDateTime.now()
                .plus(SESSION_TTL)
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        sessions.put(token, new Session(operator.getId(), expiresAt));
        sweepExpired();
        return new OperatorSessionResponse(token, expiresAt);
    }

    /**
     * 이 토큰이 그 운영자의 살아 있는 세션인지.
     *
     * 다른 운영자의 토큰으로는 통과하지 못한다 — 토큰만 맞으면 되게 두면, 계정이 바뀌어도
     * 같은 열쇠가 계속 듣는다.
     */
    public boolean isValid(String token, Long operatorUserId) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt().isBefore(OffsetDateTime.now())) {
            sessions.remove(token);
            return false;
        }
        return session.operatorUserId().equals(operatorUserId);
    }

    private void requireNotLocked(Long operatorId) {
        Failures current = failures.get(operatorId);
        if (current == null || current.lockedUntil() == null) {
            return;
        }
        if (current.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.OPERATOR_OTP_LOCKED);
        }
        failures.remove(operatorId);
    }

    private void recordFailure(Long operatorId) {
        Failures current = failures.getOrDefault(operatorId, new Failures(0, null));
        int count = current.count() + 1;
        failures.put(operatorId, count >= MAX_FAILURES
                ? new Failures(count, OffsetDateTime.now().plus(LOCK_DURATION))
                : new Failures(count, null));
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 만료된 세션을 치운다 — 발급할 때마다 한 번이면 충분하다(수가 적다). */
    private void sweepExpired() {
        OffsetDateTime now = OffsetDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Session(Long operatorUserId, OffsetDateTime expiresAt) {
    }

    private record Failures(int count, OffsetDateTime lockedUntil) {
    }
}
