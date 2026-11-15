package com.kickoff.be.chatlink.client;

import com.kickoff.be.chatlink.dto.ChatEventOutcome;
import com.kickoff.be.chatlink.dto.ChatEventResult;
import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.entity.ChatEventType;
import com.kickoff.be.chatlink.security.InternalSecretFilter;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
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

    /**
     * 이벤트 한 건을 보낸다.
     *
     * <b>상태를 종류별로 가른다</b>(계약서 §10). "2xx 인가"만 보면 400 을 받은 행이 영원히
     * 재시도된다 — 채팅 서버는 모르는 type·깨진 본문에 400 을 주는데, 그건 기다린다고
     * 달라지지 않는다. 어떻게 가르는지는 {@link #outcomeOf} 에 있다.
     */
    public ChatEventResult send(ChatOutboxMessage message) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(properties.url() + PATH)
                    .header(InternalSecretFilter.HEADER, properties.secret())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body(message))
                    .retrieve()
                    .toBodilessEntity();
            return ChatEventResult.delivered(response.getStatusCode().value());
        } catch (HttpStatusCodeException e) {
            // 응답은 받았다 — 채팅 서버는 살아 있고, 무엇이 문제인지 상태가 말해 준다.
            int status = e.getStatusCode().value();
            log.debug("채팅 이벤트 거절 — eventId={}, status={}", message.eventId(), status);
            return ChatEventResult.of(outcomeOf(status), status);
        } catch (RuntimeException e) {
            // 연결 실패·타임아웃. 채팅 서버가 꺼져 있거나 네트워크가 끊겼다 — 기다리면 풀린다.
            log.debug("채팅 이벤트 전달 실패 — eventId={}", message.eventId(), e);
            return ChatEventResult.noResponse();
        }
    }

    /**
     * 상태 코드를 "다음에 무엇을 할지"로 옮긴다 (계약서 §10).
     *
     * <b>408·429 는 4xx 지만 재시도 쪽이다.</b> 둘 다 "지금은 안 되니 나중에"라는 뜻이라,
     * 내용이 틀렸다는 400 과 같이 취급하면 멀쩡한 이벤트를 다섯 번 만에 버리게 된다.
     * 429 는 특히 우리가 50건을 몰아 보낸 직후에 나올 수 있는 답이다.
     */
    static ChatEventOutcome outcomeOf(int status) {
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return ChatEventOutcome.UNAUTHORIZED;
        }
        if (status == HttpStatus.REQUEST_TIMEOUT.value()
                || status == HttpStatus.TOO_MANY_REQUESTS.value()
                || status >= 500) {
            return ChatEventOutcome.RETRY;
        }
        return status >= 400 ? ChatEventOutcome.REJECTED : ChatEventOutcome.RETRY;
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
