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

    /**
     * 탈퇴할 때 그 번호의 인증 이력을 지운다 (방침 §3-1 "탈퇴 시 지체 없이 파기").
     *
     * <b>전화번호가 평문 컬럼</b>이라, 남겨 두면 탈퇴한 사람의 번호가 DB 에 계속 남는다.
     * 코드·토큰은 해시라 그 자체로는 문제가 없지만 번호는 그렇지 않다.
     */
    long deleteByPhone(String phone);

    /**
     * 보관 기간이 지난 이력을 지운다 (방침 §3-2 "일정 기간 보관 후 파기").
     *
     * 파생 메서드로 두는 이유가 있다. 같은 일을 {@code @Query} 로 적으면 JPQL 문자열이
     * 생기고, 그건 H2 에서 통과하고 PostgreSQL 에서 깨지는 자리를 하나 더 만드는 셈이다
     * (전례: {@code lower(concat(...))} 가 null 에서 bytea 로 추론돼 운영 500).
     * 파생 메서드는 스프링이 방언에 맞춰 생성하므로 그 위험이 없다.
     */
    long deleteByCreatedAtBefore(OffsetDateTime cutoff);
}
