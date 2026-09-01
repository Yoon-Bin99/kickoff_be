package com.kickoff.be.support.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 어댑터 선택 (계약서 §7-1, v1.22.0).
 *
 * <b>키가 있을 때만 등록된다.</b> 빈 키로라도 등록하면 매 요청이 인증 실패로 강등되는데,
 * 그건 "설정이 빠졌다"가 아니라 "AI 가 이상하다"처럼 보인다 — 문자 어댑터와 같은 이유다.
 *
 * <b>스위치({@code AI_SUPPORT_ENABLED})는 여기서 보지 않는다.</b> 서비스가 본다.
 * 등록 조건에 스위치를 넣으면 "키는 있는데 꺼 둔" 상태와 "키가 없는" 상태가 한 모양이
 * 되어, 왜 AI 가 안 붙는지 구별할 수 없다. 문자에서 같은 판단을 한 적이 있다 —
 * 스위치 검사를 어댑터 안에 두면 dev 에서 진짜 문자가 나갈 뻔했다.
 */
@Configuration
public class AiSupportClientConfig {

    @Bean
    @ConditionalOnExpression("!'${kickoff.ai-support.api-key:}'.isEmpty()")
    public AiSupportClient anthropicAiSupportClient(AiSupportProperties properties) {
        return new AnthropicAiSupportClient(properties);
    }
}
