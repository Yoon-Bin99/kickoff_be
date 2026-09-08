package com.kickoff.be.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.passwordreset.entity.PasswordResetCode;
import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.verification.entity.PhoneVerification;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 보관 기간 파기 (개인정보처리방침 §3-2).
 *
 * 방침이 "일정 기간 보관 후 파기"를 약속해 두고 파기가 구현되지 않아, 전화번호와 이메일이
 * 평문으로 무한히 쌓이던 것을 닫는 작업이다. 그래서 <b>경계 양쪽</b>을 다 본다 — 지워야 할
 * 것을 안 지우면 방침 위반이 그대로고, 남겨야 할 것을 지우면 진행 중인 인증이 끊긴다.
 *
 * {@code createdAt} 은 {@code @CreatedDate} 라 저장할 때 결정된다. 과거 시각을 만들려면
 * 저장한 뒤 직접 되돌려 놓아야 해서, 리포지토리로 저장하고 시각만 밀어 넣는다.
 */
class RetentionCleanupJobTest extends IntegrationTestSupport {

    @Autowired
    private RetentionCleanupJob job;

    @Test
    @DisplayName("전화 인증 이력은 30일이 지나면 지우고 그 전이면 남긴다")
    void purgesOldPhoneVerifications() {
        savePhoneVerification("010-0000-0001", daysAgo(31));
        savePhoneVerification("010-0000-0002", daysAgo(29));

        job.purgeExpiredRecords();

        assertThat(phoneVerificationRepository.findFirstByPhoneOrderByIdDesc("010-0000-0001"))
                .as("31일 전 이력은 파기된다")
                .isEmpty();
        assertThat(phoneVerificationRepository.findFirstByPhoneOrderByIdDesc("010-0000-0002"))
                .as("29일 전 이력은 남는다 — 아직 보관 기간 안이다")
                .isPresent();
    }

    @Test
    @DisplayName("재설정 이력은 7일이 지나면 지우고 그 전이면 남긴다")
    void purgesOldPasswordResetCodes() {
        savePasswordResetCode("old@example.com", daysAgo(8));
        savePasswordResetCode("recent@example.com", daysAgo(6));

        job.purgeExpiredRecords();

        assertThat(passwordResetCodeRepository.findFirstByEmailOrderByIdDesc("old@example.com"))
                .as("8일 전 이력은 파기된다")
                .isEmpty();
        assertThat(passwordResetCodeRepository.findFirstByEmailOrderByIdDesc("recent@example.com"))
                .as("6일 전 이력은 남는다")
                .isPresent();
    }

    /**
     * 기간이 다른 것이 의도임을 고정한다. 한 값으로 합치고 싶어지는 자리라, 둘을 같은
     * 시각으로 두고 한쪽만 지워지는지 본다 — 8일 전이면 재설정은 지나고 인증은 안 지났다.
     */
    @Test
    @DisplayName("두 표의 보관 기간은 서로 다르다")
    void retentionDiffersPerTable() {
        savePhoneVerification("010-0000-0003", daysAgo(8));
        savePasswordResetCode("both@example.com", daysAgo(8));

        job.purgeExpiredRecords();

        assertThat(phoneVerificationRepository.findFirstByPhoneOrderByIdDesc("010-0000-0003"))
                .as("전화 인증은 30일이라 8일 전은 남는다")
                .isPresent();
        assertThat(passwordResetCodeRepository.findFirstByEmailOrderByIdDesc("both@example.com"))
                .as("재설정은 7일이라 8일 전은 지워진다")
                .isEmpty();
    }

    private OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now().minusDays(days);
    }

    private void savePhoneVerification(String phone, OffsetDateTime createdAt) {
        PhoneVerification saved = phoneVerificationRepository.save(
                PhoneVerification.issue(phone, "hash", OffsetDateTime.now().plusMinutes(3)));
        backdate("phone_verifications", saved.getId(), createdAt);
    }

    private void savePasswordResetCode(String email, OffsetDateTime createdAt) {
        PasswordResetCode saved = passwordResetCodeRepository.save(
                PasswordResetCode.issued(email, "hash", OffsetDateTime.now().plusMinutes(10)));
        backdate("password_reset_codes", saved.getId(), createdAt);
    }

    /**
     * {@code createdAt} 은 {@code @CreatedDate} 라 저장 시점에 정해지고 {@code updatable = false}
     * 라 JPA 로는 못 바꾼다. 과거 행을 만들 방법이 네이티브 update 뿐이다.
     *
     * 트랜잭션 밖이라 {@code TransactionTemplate} 으로 감싼다 — 테스트에 트랜잭션을 걸지
     * 않는 것이 이 저장소의 관례다.
     */
    private void backdate(String table, Long id, OffsetDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status ->
                entityManager.createNativeQuery(
                                "update " + table + " set created_at = :createdAt where id = :id")
                        .setParameter("createdAt", createdAt)
                        .setParameter("id", id)
                        .executeUpdate());
        entityManager.clear();
    }
}
