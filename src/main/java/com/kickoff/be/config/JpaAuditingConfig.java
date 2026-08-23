package com.kickoff.be.config;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * 기본 provider 는 LocalDateTime 을 주는데, 계약서가 오프셋 포함 ISO-8601 을 요구하므로
     * OffsetDateTime 으로 직접 준다. 소수점 자리는 ErrorResponse 와 맞춰 밀리초까지만 쓴다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
