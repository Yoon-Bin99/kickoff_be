package com.kickoff.be.push.client;

import com.kickoff.be.push.dto.PushMessage;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Expo Push Service 로 보낸다 (계약서 §8).
 *
 * <b>어떤 경우에도 예외를 밖으로 내지 않는다.</b> 이 호출은 이미 커밋된 트랜잭션 뒤에
 * 일어나므로, 여기서 터뜨려봐야 되돌릴 것도 없고 원 요청만 망가진다. 실패는 로그로 남긴다.
 *
 * Expo 는 200 을 주면서 본문에 개별 메시지의 실패를 담아 보낸다 (DeviceNotRegistered 등).
 * HTTP 상태만 보고 성공으로 넘기면 조용히 사라지므로 본문의 status 도 훑는다.
 */
@Slf4j
@Component
public class ExpoPushClient implements PushClient {

    private static final String SEND_URL = "https://exp.host/--/api/v2/push/send";

    private final RestClient restClient;

    public ExpoPushClient() {
        this.restClient = RestClient.create();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void send(List<PushMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }
        try {
            Map<String, Object> body = restClient.post()
                    .uri(SEND_URL)
                    .header("accept", "application/json")
                    .body(messages)
                    .retrieve()
                    .body(Map.class);
            logTicketErrors(body);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패 — {}건, 사유: {}", messages.size(), e.getMessage());
        }
    }

    /** 200 이어도 개별 메시지는 실패할 수 있다. 토큰이 죽은 경우가 대부분이다. */
    @SuppressWarnings("unchecked")
    private void logTicketErrors(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof List<?> tickets)) {
            return;
        }
        for (Object ticket : tickets) {
            if (ticket instanceof Map<?, ?> map && "error".equals(map.get("status"))) {
                log.warn("푸시 티켓 실패 — {}", map);
            }
        }
    }
}
