package com.kickoff.be.email.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 메일 설정 (계약서 §3-3, v1.23.0).
 *
 * @param enabled 기본 false. 꺼져 있으면 <b>실제로 보내지 않고 코드를 서버 로그로만</b>
 *                남긴다 — dev 에서 실수로 남의 메일함에 발송되지 않게. 문자와 같은 방침이다.
 * @param from    발신 주소. 비어 있으면 username 을 쓴다.
 */
@ConfigurationProperties(prefix = "kickoff.email")
public record EmailProperties(boolean enabled, String from) {
}
