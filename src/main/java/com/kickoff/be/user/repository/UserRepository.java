package com.kickoff.be.user.repository;

import com.kickoff.be.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * refresh token 해시로 사용자를 찾는다 (계약서 §3, v1.7.0).
     * 만료 확인은 여기서 하지 않는다 — 엔티티가 판단한다.
     */
    Optional<User> findByRefreshTokenHash(String refreshTokenHash);

    /** 소셜 신규 가입 때 닉네임이 겹치는지 본다 (계약서 §3-1 규칙 3). */
    boolean existsByNickname(String nickname);
}
