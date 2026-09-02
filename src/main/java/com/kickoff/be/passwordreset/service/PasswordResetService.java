package com.kickoff.be.passwordreset.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.passwordreset.dto.PasswordResetConfirmRequest;
import com.kickoff.be.passwordreset.dto.PasswordResetRequest;
import com.kickoff.be.passwordreset.entity.PasswordResetCode;
import com.kickoff.be.passwordreset.repository.PasswordResetCodeRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 (계약서 §3-3, v1.23.0).
 *
 * <b>이 기능의 규칙은 거의 전부 "계정 존재를 노출하지 않는 것"이다.</b> 발송 API 는 항상
 * 204 고, 확인 실패는 만료·코드 없음·계정 없음이 전부 같은 응답이며, 한도조차 계정
 * 유무와 무관해야 한다. 셋 중 하나만 어겨도 이 API 가 계정 확인 도구가 된다.
 *
 * §3-2 문자 인증과 같은 골격이지만 <b>토큰이 없다.</b> 문자는 "인증했다"는 증표를 가입까지
 * 들고 가야 하지만, 여기서는 코드 확인과 비밀번호 교체가 한 요청에서 끝난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    /** 이메일은 문자보다 지연이 커서 10분이다 (계약서 §3-3 — 문자는 3분). */
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RATE_WINDOW_SHORT = Duration.ofMinutes(1);
    private static final Duration RATE_WINDOW_LONG = Duration.ofHours(1);
    private static final long RATE_LIMIT_SHORT = 1;
    private static final long RATE_LIMIT_LONG = 5;
    private static final int CODE_BOUND = 1_000_000;

    private final PasswordResetCodeRepository repository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();

    /**
     * 코드 발송 요청 — <b>항상 204</b> (계약서 §3-3).
     *
     * 순서가 규칙이다. 한도를 <b>계정을 찾기 전에</b> 본다 — 존재 여부가 한도 판정에 끼어들
     * 자리를 아예 없앤다. 그리고 계정이 없어도 행을 남긴다.
     */
    @Transactional
    public void request(PasswordResetRequest request) {
        String email = normalize(request.email());
        OffsetDateTime now = OffsetDateTime.now();
        requireUnderRateLimit(email, now);

        Optional<User> account = emailAccountOf(email);
        if (account.isEmpty()) {
            // 계정이 없거나 소셜 전용이다. 행만 남기고 조용히 끝낸다 — 여기서 다르게
            // 행동하면(예: 행을 안 남기면) 다음 요청의 한도 응답이 존재를 말해 준다.
            repository.save(PasswordResetCode.recordedOnly(email));
            return;
        }

        String code = generateCode();
        repository.save(PasswordResetCode.issued(
                email, passwordEncoder.encode(code), now.plus(CODE_TTL)));
        // 발송은 커밋 이후 비동기다. 여기서 SMTP 를 기다리면 응답이 그만큼 늦고,
        // 그 지연 차이가 "계정이 있다"는 신호가 된다.
        eventPublisher.publishEvent(new PasswordResetCodeIssuedEvent(email, code));
    }

    /**
     * 코드 확인 + 비밀번호 교체 (계약서 §3-3).
     *
     * 실패 응답이 두 가지뿐이다. 코드가 틀렸으면 {@code VERIFICATION_CODE_MISMATCH},
     * <b>그 밖의 모든 실패는 {@code VERIFICATION_EXPIRED}</b> — 만료든, 코드를 받은 적이
     * 없든, 계정이 없든. 갈라 놓으면 그 차이가 곧 존재 여부다.
     *
     * <b>{@code noRollbackFor} 도 이 메서드의 핵심이다.</b> 실패 횟수를 올린 뒤 예외를
     * 던지는데, 기본 설정이면 그 예외가 트랜잭션을 되돌려 <b>증가가 통째로 사라진다</b>.
     * 그러면 "5회 연속 실패 시 코드 무효"가 영원히 발동하지 않아 6자리를 무제한으로
     * 찍어볼 수 있다 — 응답은 400 으로 똑같아 겉으로는 아무 문제가 없어 보인다.
     *
     * 전화 인증(§3-2)에서 같은 함정을 겪고 주석까지 남겨 뒀는데 여기서 되풀이했다.
     * 6번째 시도의 응답을 보는 테스트가 잡았다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void confirm(PasswordResetConfirmRequest request) {
        String email = normalize(request.email());
        OffsetDateTime now = OffsetDateTime.now();

        PasswordResetCode reset = repository.findFirstByEmailOrderByIdDesc(email)
                .filter(found -> found.isUsable(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_EXPIRED));

        if (!passwordEncoder.matches(request.code(), reset.getCodeHash())) {
            // 5회를 넘기면 isUsable 이 false 가 되어 다음부터는 EXPIRED 로 나간다.
            reset.recordFailedAttempt();
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        }

        User user = emailAccountOf(email)
                // 코드를 받은 뒤 탈퇴한 경우다. 여기서 USER_NOT_FOUND 를 주면 "있었다"가
                // 드러나므로 같은 EXPIRED 로 덮는다.
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_EXPIRED));

        user.updatePassword(passwordEncoder.encode(request.newPassword()));
        // 탈취 대응의 핵심이다 (계약서 §3-3). 비밀번호를 바꿔도 남의 기기에 살아 있는
        // refresh 가 그대로면 계정을 되찾지 못한다.
        user.clearRefreshToken();
        reset.consume();
    }

    /**
     * 이 이메일로 <b>비밀번호를 가진</b> 계정. 소셜 전용 계정은 비밀번호가 없어 재설정
     * 대상이 아니다 (계약서 §3-3 — 이메일 계정 전용).
     *
     * 대소문자를 무시해 찾는다. 가입 판정이 무시 비교라 "Kim@..." 으로 가입한 사람이
     * "kim@..." 으로 재설정을 요청하는 게 정상 경로다.
     */
    private Optional<User> emailAccountOf(String email) {
        return userRepository.findAllByEmailIgnoreCase(email).stream()
                .filter(User::hasPassword)
                .findFirst();
    }

    private void requireUnderRateLimit(String email, OffsetDateTime now) {
        if (repository.countByEmailAndCreatedAtAfter(email, now.minus(RATE_WINDOW_SHORT))
                >= RATE_LIMIT_SHORT
                || repository.countByEmailAndCreatedAtAfter(email, now.minus(RATE_WINDOW_LONG))
                >= RATE_LIMIT_LONG) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMITED);
        }
    }

    /** 6자리. 앞자리가 0 이어도 여섯 글자여야 한다. */
    private String generateCode() {
        return "%06d".formatted(random.nextInt(CODE_BOUND));
    }

    /** 대소문자만 바꿔 한도를 우회하지 못하게 한다. */
    private String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
