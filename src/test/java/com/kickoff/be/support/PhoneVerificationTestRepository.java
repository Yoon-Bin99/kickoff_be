package com.kickoff.be.support;

import com.kickoff.be.verification.entity.PhoneVerification;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테스트가 시각을 앞당기는 통로 (계약서 §3-2 검증용).
 *
 * 3분·10분·1시간을 실제로 기다릴 수는 없다. 시계를 주입 가능하게 바꾸는 방법도 있지만
 * 운영 코드가 테스트 사정으로 복잡해지고, 정작 검증하려는 성질("만료를 판정하는가")과는
 * 멀어진다. 저장된 시각을 과거로 당기는 쪽이 그 성질에 더 가깝다.
 *
 * <b>JDBC 로 직접 쓰지 않고 JPQL 로 쓰는 게 중요하다.</b> 시각 컬럼은
 * {@code timezone.default_storage: NORMALIZE} 로 UTC 정규화되어 들어가는데, 드라이버로
 * 직접 쓰면 그 변환을 건너뛰어 9시간 어긋난 값이 들어간다. 그러면 만료 테스트는 우연히
 * 통과하고(어차피 과거니까) 레이트리밋 테스트만 이유 없이 깨진다.
 */
public interface PhoneVerificationTestRepository extends Repository<PhoneVerification, Long> {

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update PhoneVerification v set v.expiresAt = :at")
    void expireCodesAt(@Param("at") OffsetDateTime at);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update PhoneVerification v set v.tokenExpiresAt = :at")
    void expireTokensAt(@Param("at") OffsetDateTime at);

    /** 레이트리밋만 비켜 간다 — 발송 이력의 시각을 과거로 당긴다. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update PhoneVerification v set v.createdAt = :at")
    void backdateSendsTo(@Param("at") OffsetDateTime at);
}
