package com.kickoff.be.verification.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.sms.client.SmsClient;
import com.kickoff.be.verification.entity.PhoneVerification;
import com.kickoff.be.verification.repository.PhoneVerificationRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전화번호 문자 인증 (계약서 §3-2, v1.15.0).
 *
 * 번호가 <b>본인 소유인지</b>만 본다. 실명 확인이 아니다 — PASS 같은 본인확인기관은
 * 범위 밖이다(§9).
 *
 * 흐름은 두 단계다. 발송으로 6자리 코드를 만들어 보내고, 확인에 성공하면 그 번호에 묶인
 * verificationToken 을 준다. 가입·번호 변경은 그 토큰을 제출한다. 코드를 그대로 가입
 * 요청에 싣지 않는 이유는, 그러면 코드가 여러 화면을 돌아다니며 유효 시간 내내 재사용
 * 가능해지기 때문이다. 토큰은 1회용이라 쓰는 순간 죽는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    /** 계약서 §3-2 가 정한 값들. 바꾸면 FE 타이머와 어긋난다. */
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration RATE_WINDOW_SHORT = Duration.ofMinutes(1);
    private static final Duration RATE_WINDOW_LONG = Duration.ofHours(1);
    private static final long RATE_LIMIT_SHORT = 1;
    private static final long RATE_LIMIT_LONG = 5;

    private static final String MESSAGE_FORMAT = "[킥오프] 인증번호 %s를 입력해 주세요.";
    private static final int TOKEN_BYTES = 32;

    private final PhoneVerificationRepository repository;
    private final SmsClient smsClient;
    private final PasswordEncoder passwordEncoder;
    private final VerificationProperties properties;

    private final SecureRandom random = new SecureRandom();

    /**
     * 인증번호 발송 (계약서 §3-2). 응답에 코드를 싣지 않는다 — 문자를 받을 수 있는
     * 사람만 알아야 인증이 성립한다.
     *
     * 발송이 트랜잭션 안에 있는 건 의도다. 밖으로 빼면 "행은 남았는데 문자는 안 간" 상태가
     * 생기고, 사용자는 오지 않는 문자를 3분간 기다린다. 실패하면 행째로 되돌려 재요청이
     * 레이트리밋에 걸리지 않게 한다 — 못 보낸 발송을 한도에 세는 건 부당하다.
     */
    @Transactional
    public void send(String phone) {
        OffsetDateTime now = OffsetDateTime.now();
        requireWithinRateLimit(phone, now);

        String code = newCode();
        repository.save(PhoneVerification.issue(phone, passwordEncoder.encode(code),
                now.plus(CODE_TTL)));
        dispatch(phone, code);
    }

    /**
     * 어떤 발송 실패든 계약이 정한 502 SMS_SEND_FAILED 로 바꾼다 (계약서 §3-2).
     *
     * 제공자 어댑터는 저마다 자기 예외를 던진다 — HTTP 클라이언트 예외, 타임아웃,
     * 파싱 실패. 그대로 새어 나가면 500 INTERNAL_ERROR 가 되어, FE 는 "서버가 고장났다"로
     * 읽고 사용자는 재시도조차 안내받지 못한다. 실제로 이 감싸기가 없던 동안 스텁 제공자의
     * 예외가 500 으로 나갔고, 테스트가 그걸 잡았다.
     */
    private void dispatch(String phone, String code) {
        try {
            smsClient.send(phone, MESSAGE_FORMAT.formatted(code));
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("인증번호 발송 실패 — phone={}", phone, e);
            throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
        }
    }

    /**
     * 인증번호 확인 → verificationToken (계약서 §3-2).
     *
     * <b>noRollbackFor 가 이 메서드의 핵심이다.</b> 실패 횟수를 올린 뒤 예외를 던지는데,
     * 기본 설정이면 그 예외가 트랜잭션을 되돌려 <b>증가가 통째로 사라진다</b>. 그러면
     * "5회 연속 실패 시 코드 무효"가 영원히 발동하지 않아, 6자리를 무제한으로 찍어볼 수
     * 있게 된다. 응답은 400 으로 똑같아서 겉으로는 아무 문제가 없어 보인다 —
     * PhoneVerificationTest 가 6번째 시도의 응답으로 이걸 잡는다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public String confirm(String phone, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        PhoneVerification verification = repository.findFirstByPhoneOrderByIdDesc(phone)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_EXPIRED));

        // 만료와 "5회 틀려서 무효"를 같은 코드로 답한다. 어느 쪽이든 사용자가 할 일은
        // 재발송 하나뿐이고, 구분해 주면 몇 번 남았는지 세어 볼 수 있게 된다.
        if (!verification.isCodeUsable(now)) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        if (!passwordEncoder.matches(code, verification.getCodeHash())) {
            verification.recordFailedAttempt();
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        String token = newToken();
        verification.issueToken(passwordEncoder.encode(token), now.plus(TOKEN_TTL));
        return token;
    }

    /**
     * 가입·번호 변경이 제출한 토큰을 소비한다 (계약서 §3-2).
     *
     * 스위치가 꺼져 있어도 <b>토큰이 왔으면 검증한다.</b> 없을 때만 통과시킨다. 무효
     * 토큰을 조용히 삼키면, 스위치를 켜는 날 갑자기 가입이 막히는 사용자가 생기고 그때는
     * 원인을 되짚기 어렵다. "지금은 강제하지 않는다"와 "아무거나 받는다"는 다른 이야기다.
     */
    @Transactional
    public void consume(String phone, String token) {
        if (token == null || token.isBlank()) {
            if (properties.phoneRequired()) {
                throw new BusinessException(ErrorCode.PHONE_NOT_VERIFIED);
            }
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        PhoneVerification verification = repository
                .findFirstByPhoneAndTokenHashIsNotNullOrderByIdDesc(phone)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHONE_NOT_VERIFIED));

        // 번호 불일치도 여기서 걸린다 — 다른 번호로 인증한 토큰은 이 번호의 행에서
        // 찾아지지 않는다. 계약서가 "전화번호 불일치"를 PHONE_NOT_VERIFIED 로 둔 이유다.
        if (!passwordEncoder.matches(token, verification.getTokenHash())) {
            throw new BusinessException(ErrorCode.PHONE_NOT_VERIFIED);
        }
        if (verification.isTokenUsed()) {
            throw new BusinessException(ErrorCode.PHONE_NOT_VERIFIED);
        }
        if (verification.isTokenExpired(now)) {
            throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        }
        verification.useToken(now);
    }

    /**
     * 같은 번호로 1분에 1회, 1시간에 5회 (계약서 §3-2).
     *
     * 두 창을 함께 보는 이유는 성질이 다르기 때문이다. 1분 제한은 연타를, 1시간 제한은
     * 문자 요금을 태우는 반복 요청을 막는다.
     */
    private void requireWithinRateLimit(String phone, OffsetDateTime now) {
        if (repository.countByPhoneAndCreatedAtAfter(phone, now.minus(RATE_WINDOW_SHORT))
                >= RATE_LIMIT_SHORT
                || repository.countByPhoneAndCreatedAtAfter(phone, now.minus(RATE_WINDOW_LONG))
                >= RATE_LIMIT_LONG) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMITED);
        }
    }

    /**
     * 6자리 인증번호. {@code %06d} 로 채우는 게 중요하다 — 그냥 숫자를 찍으면 앞자리가
     * 0 인 값이 5자리로 나가서, 화면의 6칸 입력과 어긋나는 코드가 10 번에 1 번꼴로 발송된다.
     */
    private String newCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    /** 추측할 수 없어야 하므로 SecureRandom 이다. URL 에 실릴 수도 있어 URL 안전 인코딩. */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
