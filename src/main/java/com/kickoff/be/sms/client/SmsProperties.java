package com.kickoff.be.sms.client;

/**
 * @param enabled 기본 false (계약서 §3-2). dev 에서는 실제 발송을 생략하고 인증번호를
 *                서버 로그로만 남긴다. 운영에서 true 로 켜되, 그때는 제공자 어댑터가
 *                반드시 함께 있어야 한다 — 없으면 502 로 시끄럽게 실패한다.
 */
@org.springframework.boot.context.properties.ConfigurationProperties(prefix = "kickoff.sms")
public record SmsProperties(boolean enabled) {
}
