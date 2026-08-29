package com.kickoff.be.verification.repository;

import com.kickoff.be.verification.entity.PhoneVerification;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhoneVerificationRepository extends JpaRepository<PhoneVerification, Long> {

    /**
     * 이 번호의 <b>가장 최근</b> 발송. 재발송하면 이전 코드가 무효가 된다는 규칙이
     * 여기서 저절로 지켜진다 — 옛 행은 아무도 보지 않는다 (계약서 §3-2).
     */
    Optional<PhoneVerification> findFirstByPhoneOrderByIdDesc(String phone);

    /** 토큰이 얹힌 가장 최근 행. 토큰 소비할 때만 쓴다. */
    Optional<PhoneVerification> findFirstByPhoneAndTokenHashIsNotNullOrderByIdDesc(String phone);

    /** 레이트리밋 (계약서 §3-2) — 발송 한 번이 한 행이라 그냥 세면 된다. */
    long countByPhoneAndCreatedAtAfter(String phone, OffsetDateTime since);
}
