package com.kickoff.be.common;

import com.kickoff.be.passwordreset.repository.PasswordResetCodeRepository;
import com.kickoff.be.verification.repository.PhoneVerificationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보관 기간이 지난 인증 이력을 지운다 (개인정보처리방침 §3-2).
 *
 * <b>왜 필요한가.</b> 방침은 "인증 이력은 일정 기간 보관 후 파기한다"고 약속했는데, 그
 * 파기가 구현된 적이 없었다. 두 표는 계정과 외래키로 이어져 있지 않아 탈퇴로도 정리되지
 * 않고(가입 전에도 쓰이는 표다), 그래서 <b>전화번호와 이메일이 평문으로 무한히 쌓이고
 * 있었다.</b> 코드·토큰은 BCrypt 해시라 문제가 아니고, 지우려는 것은 번호와 주소다.
 *
 * 기간이 다른 이유. 인증 이력은 <b>부정 이용 추적</b>에 쓸 여지가 있어 여유를 두고,
 * 재설정 이력은 본인 계정에만 일어나는 일이라 그럴 이유가 없어 짧게 잡는다.
 *
 * <b>기동 시에는 돌지 않는다.</b> {@code @Scheduled} 만 걸고 초기 실행을 두지 않은 것은
 * 의도다 — 재배포가 잦은 환경에서 기동마다 대량 삭제가 도는 건 이 작업의 성격과 맞지
 * 않고, 배포 직후 로그를 읽을 때 불필요한 잡음이 된다.
 *
 * 인스턴스가 여럿이면 같은 시각에 함께 돌 수 있다. 삭제는 멱등이라(이미 지운 행은 다시
 * 걸리지 않는다) 지금 구조로는 문제가 없다. 스케줄 잠금이 필요해지는 건 작업이 멱등을
 * 잃을 때다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionCleanupJob {

    /** 부정 이용 추적에 쓸 여지를 남기는 기간. 방침 v2 에 같은 값이 적힌다. */
    private static final Duration PHONE_VERIFICATION_RETENTION = Duration.ofDays(30);

    /** 본인 계정에만 일어나는 일이라 추적 목적이 없다. */
    private static final Duration PASSWORD_RESET_RETENTION = Duration.ofDays(7);

    private final PhoneVerificationRepository phoneVerificationRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;

    /**
     * 매일 새벽 4시 30분(KST). 사용자가 가장 적은 시간대다.
     *
     * 시간대를 못박은 이유: 서버가 어느 리전에 뜨든 같은 시각에 돌아야 한다. 운영은
     * 미국 리전이라 zone 을 빼면 한국 기준 낮에 도는 일이 생긴다.
     */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purgeExpiredRecords() {
        OffsetDateTime now = OffsetDateTime.now();
        long verifications = phoneVerificationRepository
                .deleteByCreatedAtBefore(now.minus(PHONE_VERIFICATION_RETENTION));
        long resets = passwordResetCodeRepository
                .deleteByCreatedAtBefore(now.minus(PASSWORD_RESET_RETENTION));

        // 건수만 남긴다. 무엇을 지웠는지 적으면 파기 목적으로 도는 작업이 그 값을
        // 로그에 다시 심는 꼴이 된다.
        log.info("보관 기간 경과 이력 파기 — 전화 인증 {}건, 비밀번호 재설정 {}건",
                verifications, resets);
    }
}
