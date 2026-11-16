package com.kickoff.be.operator.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자 재탈퇴 API 의 스위치 (계약서 §7-2 B-6, v1.37.3).
 *
 * <b>평시에는 꺼 둔다.</b> "운영자가 아무 계정이나 지울 수 있는 API"가 상시 살아 있으면,
 * 운영자 계정이 뚫렸을 때 그것이 곧 대량 삭제 수단이 된다. 백업을 복원하는 동안에만 켜고
 * 끝나면 끈다.
 *
 * 꺼져 있으면 404 다 — 403 이 아니다. 있는 줄도 모르게 두는 편이 낫다.
 *
 * @param enabled 환경변수 {@code ADMIN_ACCOUNT_DELETION_ENABLED}. 기본 false
 */
@ConfigurationProperties(prefix = "kickoff.admin.account-deletion")
public record AdminDeletionProperties(Boolean enabled) {

    public AdminDeletionProperties {
        enabled = enabled != null && enabled;
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
