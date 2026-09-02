package com.kickoff.be.passwordreset.repository;

import com.kickoff.be.passwordreset.entity.PasswordResetCode;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    /**
     * 이 이메일의 <b>가장 최근</b> 요청. 재요청하면 이전 코드가 무효가 된다는 규칙이
     * 여기서 저절로 지켜진다 — 옛 행은 아무도 보지 않는다 (계약서 §3-3).
     */
    Optional<PasswordResetCode> findFirstByEmailOrderByIdDesc(String email);

    /**
     * 레이트리밋 (계약서 §3-3) — <b>코드가 없는 행도 센다.</b> 그래야 한도가 계정 유무와
     * 무관해지고, 429 가 존재 신호가 되지 않는다.
     */
    long countByEmailAndCreatedAtAfter(String email, OffsetDateTime since);
}
