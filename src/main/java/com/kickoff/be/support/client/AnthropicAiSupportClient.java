package com.kickoff.be.support.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Anthropic Messages API 어댑터 (계약서 §7-1, v1.22.0).
 *
 * <b>이 클래스는 실패를 한 종류로 모은다.</b> 타임아웃·인증 실패·크레딧 부족·응답 형식
 * 변경이 전부 {@link AiUnavailableException} 으로 나가고, 호출자는 그걸 받아 강등 안내를
 * 저장한다. 여기서 예외가 새어 나가면 사용자 메시지 전송 자체가 500 이 되는데,
 * <b>AI 가 답을 못 한다고 사용자의 말이 사라지면 안 된다</b> — 그 말은 이미 저장됐고
 * 운영자가 읽어야 한다.
 */
@Slf4j
public class AnthropicAiSupportClient implements AiSupportClient {

    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String VERSION = "2023-06-01";

    private final RestClient restClient;
    private final AiSupportProperties properties;

    public AnthropicAiSupportClient(AiSupportProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(properties.timeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String reply(String systemPrompt, List<AiTurn> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiTurn turn : history) {
            messages.add(Map.of("role", turn.fromUser() ? "user" : "assistant",
                    "content", turn.content()));
        }
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "system", systemPrompt,
                "messages", messages);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(URL)
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // 키가 틀렸는지, 크레딧이 없는지, 그냥 느린지는 여기서 구분하지 않는다.
            // 사용자에게는 어차피 같은 강등 안내가 나가고, 원인은 로그가 말한다.
            log.warn("AI 상담 호출 실패: {}", e.getMessage());
            throw new AiUnavailableException("AI 상담 호출 실패", e);
        }

        String text = firstText(response);
        if (text == null || text.isBlank()) {
            // 200 인데 쓸 내용이 없는 경우다. 빈 말풍선을 저장하면 사용자는 AI 가
            // 답했다고 보는데 아무것도 없다 — 차라리 강등 안내가 낫다.
            log.warn("AI 상담 응답에 본문이 없다: {}", response == null ? "null" : response.keySet());
            throw new AiUnavailableException("AI 응답이 비어 있다", null);
        }
        return text.strip();
    }

    /** {@code content: [{type:"text", text:"..."}]} 에서 첫 text 를 꺼낸다. */
    @SuppressWarnings("unchecked")
    private static String firstText(Map<String, Object> response) {
        if (response == null || !(response.get("content") instanceof List<?> content)) {
            return null;
        }
        for (Object block : content) {
            if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))
                    && map.get("text") instanceof String text) {
                return text;
            }
        }
        return null;
    }
}
