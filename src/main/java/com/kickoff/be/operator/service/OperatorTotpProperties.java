package com.kickoff.be.operator.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 운영자 일회용 비밀번호의 비밀키 (계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>비밀값이다.</b> 이 값 하나로 운영자 화면 전체가 열린다 — 문서·로그에 남기지 않는다.
 * 인증 앱에 등록할 때 쓰는 Base32 문자열이고, dev 프로파일에만 개발용 기본값이 있다.
 *
 * <b>비어 있으면 닫힌 쪽으로 실패한다</b> — 운영자 API 전체가 막힌다. 설정을 빠뜨린
 * 배포가 남의 문의와 신고된 대화를 열어 주지 않게 하려는 것이다.
 *
 * @param secret 환경변수 {@code OPERATOR_TOTP_SECRET} (Base32)
 */
@ConfigurationProperties(prefix = "kickoff.operator")
public record OperatorTotpProperties(String secret) {

    public boolean isConfigured() {
        return secret != null && !secret.isBlank();
    }
}
