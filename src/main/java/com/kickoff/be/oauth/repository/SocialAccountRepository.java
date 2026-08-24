package com.kickoff.be.oauth.repository;

import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.oauth.entity.SocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    /** 연동 이력 조회 — 로그인 직후 User 를 바로 쓰므로 같이 끌고 온다. */
    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findByProviderAndProviderUserId(AuthProvider provider,
                                                            String providerUserId);

    /** UserResponse.authProviders 용. */
    @Query("select s.provider from SocialAccount s where s.user.id = :userId order by s.id")
    List<AuthProvider> findProvidersByUserId(@Param("userId") Long userId);
}
