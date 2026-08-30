package com.kickoff.be.sms.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 솔라피(쿨SMS 통합 플랫폼) 접속 정보 (계약서 §3-2).
 *
 * <b>값은 전부 환경변수로만 들어온다.</b> 저장소에 적지 않는다 — 키는 로테이션 대상이라
 * 커밋에 남으면 이력에서 지울 수 없다. 로컬에서는 gitignore 된 local.yaml 을 쓴다
 * (소셜 로그인 키와 같은 방식이다).
 *
 * @param apiKey    SOLAPI_API_KEY
 * @param apiSecret SOLAPI_API_SECRET — 서명 키다. 로그에 찍지 말 것
 * @param sender    SMS_SENDER — 솔라피에 등록된 발신번호. 등록되지 않은 번호로 보내면 거절된다
 */
@ConfigurationProperties(prefix = "kickoff.sms.solapi")
public record SolapiProperties(String apiKey, String apiSecret, String sender) {
}
