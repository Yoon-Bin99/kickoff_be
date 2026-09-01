package com.kickoff.be.support.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 상담사 설정 (계약서 §7-1, v1.22.0).
 *
 * 키는 <b>환경변수나 gitignored local.yaml 로만</b> 들어온다 — application.yaml 에는
 * 바인딩만 있고 값이 없다.
 *
 * @param enabled 기본 false. 꺼져 있으면 AI 없이 FAQ·운영자 연결만 동작한다 (계약서 §7-1).
 * @param apiKey  Anthropic API 키
 * @param model   저비용 모델을 쓴다. 고객센터 답변 한 줄에 비싼 모델을 쓸 이유가 없다.
 * @param maxTokens 답변 길이 상한. 채팅 말풍선이라 길 필요가 없고, 비용도 여기서 잡힌다.
 * @param timeoutSeconds 응답을 기다리는 한도. 넘으면 강등 안내로 간다 —
 *                       사용자를 무한정 기다리게 하는 것보다 "운영자에게 전달할까요?"가 낫다.
 */
@ConfigurationProperties(prefix = "kickoff.ai-support")
public record AiSupportProperties(
        boolean enabled,
        String apiKey,
        String model,
        Integer maxTokens,
        Integer timeoutSeconds
) {

    public AiSupportProperties {
        model = (model == null || model.isBlank()) ? "claude-haiku-4-5-20251001" : model;
        maxTokens = (maxTokens == null || maxTokens <= 0) ? 512 : maxTokens;
        timeoutSeconds = (timeoutSeconds == null || timeoutSeconds <= 0) ? 20 : timeoutSeconds;
    }

    /**
     * 실제로 AI 를 부를 수 있는 상태인지. <b>스위치와 키가 모두 있어야 한다.</b>
     * 스위치만 켜고 키가 없으면 매 요청이 실패해 강등 안내만 반복되는데, 그건
     * "AI 가 이상하다"처럼 보이지 "설정이 빠졌다"로 보이지 않는다.
     */
    public boolean isUsable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
