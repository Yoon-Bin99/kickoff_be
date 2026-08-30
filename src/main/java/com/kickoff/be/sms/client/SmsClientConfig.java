package com.kickoff.be.sms.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

/**
 * 문자 제공자 어댑터 선택 (계약서 §3-2).
 *
 * 솔라피 어댑터는 <b>키와 발신번호가 모두 채워졌을 때만</b> 등록된다. 빈 값으로라도
 * 등록해 버리면 SMS_ENABLED=true 인 운영에서 매번 401 을 받고 502 로 나가는데, 그건
 * "설정이 빠졌다"가 아니라 "제공자가 이상하다"처럼 보인다.
 *
 * 등록되지 않으면 LoggingSmsClient 가 남는다 — dev 에서는 코드를 로그로 남기고,
 * SMS_ENABLED=true 인데 제공자가 없으면 502 로 시끄럽게 실패한다.
 */
@Configuration
public class SmsClientConfig {

    @Bean
    @Primary
    @ConditionalOnExpression("""
            !'${kickoff.sms.solapi.api-key:}'.isEmpty()
            and !'${kickoff.sms.solapi.api-secret:}'.isEmpty()
            and !'${kickoff.sms.solapi.sender:}'.isEmpty()
            """)
    public SmsClient solapiSmsClient(RestClient.Builder builder, SolapiProperties properties) {
        return new SolapiSmsClient(builder, properties);
    }
}
