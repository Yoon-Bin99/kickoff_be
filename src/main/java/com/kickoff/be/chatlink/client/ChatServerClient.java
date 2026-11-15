package com.kickoff.be.chatlink.client;

import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.entity.ChatEventType;
import com.kickoff.be.chatlink.security.InternalSecretFilter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 채팅 서버의 {@code POST /internal/events} 를 부른다 (계약서 §10).
 *
 * <b>타임아웃을 전용 팩토리로 잡는다.</b> 전역 {@code spring.http.client} 는 장소 검색
 * 기준(read 5s)인데, 이쪽은 사용자가 기다리는 호출이 아니라 백그라운드 재시도다. 길게
 * 잡아 봐야 스케줄러 한 바퀴만 늘어지고, 실패해도 다음 바퀴에 다시 보내면 된다.
 *
 * 성공/실패만 돌려준다. <b>예외를 밖으로 내지 않는다</b> — 호출자(스케줄러)는 "2xx 였나"
 * 하나만 알면 되고, 실패 종류에 따라 다르게 굴 일이 없다(전부 백오프 후 재시도다).
 */
@Slf4j
@Component
public class ChatServerClient {

    private static final String PATH = "/internal/events";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private final ChatLinkProperties properties;
    private final RestClient restClient;

    public ChatServerClient(ChatLinkProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().requestFactory(requestFactory()).build();
        if (properties.canDeliver()) {
            log.info("채팅 이벤트 전달 설정됨 — {}{}", properties.url(), PATH);
        } else {
            log.warn("채팅 이벤트 전달 꺼짐 ({}) — chat_outbox 에 쌓이기만 한다",
                    properties.hasSecret() ? "CHAT_INTERNAL_URL 없음" : "CHAT_INTERNAL_SECRET 없음");
        }
    }

    /** @return 2xx 를 받았으면 true. 그 밖(4xx·5xx·연결 실패·타임아웃)은 전부 false. */
    public boolean send(ChatOutboxMessage message) {
        try {
            restClient.post()
                    .uri(properties.url() + PATH)
                    .header(InternalSecretFilter.HEADER, properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(message))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.debug("채팅 이벤트 전달 실패 — eventId={}", message.eventId(), e);
            return false;
        }
    }

    /**
     * 계약서 §10 의 두 모양을 그대로 만든다.
     *
     * <b>{@code teamIds} 는 PURGE 에만 싣는다.</b> ROOM_INVALIDATED 의 예시에는 그 칸이
     * 아예 없다 — 빈 배열을 덧붙이면 "팀 기록도 지우라는 뜻인가"로 읽힐 여지가 생기고,
     * 받는 쪽 파서가 모르는 칸에 엄격할 수도 있다. 레코드 대신 맵을 쓰는 이유가 이것이다
     * (전역 Jackson 설정이 null 필드도 그대로 내보낸다).
     */
    Map<String, Object> body(ChatOutboxMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", message.eventId());
        body.put("type", message.type().name());
        body.put("requestIds", message.requestIds());
        if (message.type() == ChatEventType.PURGE) {
            body.put("teamIds", message.teamIds());
        }
        return body;
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
