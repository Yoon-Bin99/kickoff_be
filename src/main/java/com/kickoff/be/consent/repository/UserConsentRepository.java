package com.kickoff.be.consent.repository;

import com.kickoff.be.consent.entity.ConsentType;
import com.kickoff.be.consent.entity.UserConsent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserId(Long userId);

    Optional<UserConsent> findByUserIdAndConsentType(Long userId, ConsentType consentType);
}
