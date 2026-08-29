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

    /**
     * 닉네임 중복 판정도 대소문자를 무시한다 (계약서 §3, v1.16.0).
     *
     * 라틴 표기 닉네임에서 "Kickoff" 와 "kickoff" 를 다른 사람으로 두면 사칭에 쓰인다.
     * 저장은 입력 표기 그대로다 — 표기가 곧 그 사람이 고른 모양이라 서버가 바꾸지 않는다.
     */
    boolean existsByNicknameIgnoreCase(String nickname);

    boolean existsByPhone(String phone);

    /**
     * 이메일 중복 판정은 대소문자를 무시한다 (계약서 §3, v1.16.0).
     *
     * availability 와 signup 이 <b>같은 기준</b>을 써야 한다. 한쪽만 무시하면
     * "사용 불가라는데 가입은 되는" 값이 생긴다 — 처음에는 계약이 availability 에만
     * 규정해서 실제로 그렇게 어긋나 있었고, supervisor 가 계약을 고쳐 통일했다.
     */
    boolean existsByEmailIgnoreCase(String email);

    /** PATCH 의 중복 판정 — 자기 자신의 기존 값은 중복이 아니다 (계약서 §3, v1.16.0). */
    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    boolean existsByPhoneAndIdNot(String phone, Long id);
}
