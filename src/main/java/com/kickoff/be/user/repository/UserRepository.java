package com.kickoff.be.user.repository;

import com.kickoff.be.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 소셜 신규 가입 때 닉네임이 겹치는지 본다 (계약서 §3-1 규칙 3). */
    boolean existsByNickname(String nickname);
}
