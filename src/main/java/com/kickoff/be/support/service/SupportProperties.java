package com.kickoff.be.support.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 고객센터 운영자 지정 (계약서 §7-1, v1.22.0).
 *
 * @param operatorEmail 운영자 계정의 이메일. <b>미설정이 기본</b>이고, 그때는 문의함 API 가
 *                      전부 403 이다. 설정을 빠뜨린 배포가 남의 문의를 열어 주는 것보다
 *                      아무도 못 여는 쪽이 안전하다.
 */
@ConfigurationProperties(prefix = "kickoff.support")
public record SupportProperties(String operatorEmail) {

    public boolean isOperator(String email) {
        return operatorEmail != null && !operatorEmail.isBlank()
                && email != null && operatorEmail.equalsIgnoreCase(email);
    }
}
