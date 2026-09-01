package com.kickoff.be.push.service;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.client.PushProperties;
import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.push.dto.SupportPushEvent;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 고객센터 메시지를 푸시로 바꿔 보낸다 (계약서 §7-1·§8, v1.22.0).
 *
 * 규칙은 다른 리스너와 같다 — AFTER_COMMIT 이라 롤백된 메시지의 유령 알림이 나가지 않고,
 * @Async 라 Expo 왕복이 응답을 붙잡지 않으며, 어떤 예외도 밖으로 내지 않는다.
 * <b>발송 실패가 문의 저장을 실패시키면 안 된다</b> — 사용자의 문의가 사라지는 쪽이
 * 알림이 안 가는 것보다 훨씬 나쁘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportPushListener {

    /** 계약서 §7-1 — "내용 앞 50자". */
    private static final int BODY_LIMIT = 50;

    private final UserRepository userRepository;
    private final PushClient pushClient;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(SupportPushEvent event) {
        try {
            send(event);
        } catch (RuntimeException e) {
            log.warn("고객센터 푸시 처리 실패 — roomUserId={}, recipient={}",
                    event.roomUserId(), event.recipientUserId(), e);
        }
    }

    private void send(SupportPushEvent event) {
        if (!properties.enabled()) {
            log.debug("푸시 비활성 — 발송 생략 (roomUserId={})", event.roomUserId());
            return;
        }
        User recipient = userRepository.findById(event.recipientUserId()).orElse(null);
        if (recipient == null || !recipient.hasPushToken()) {
            log.debug("푸시 토큰 없음 — 발송 생략 (userId={})", event.recipientUserId());
            return;
        }
        pushClient.send(List.of(new PushMessage(
                recipient.getExpoPushToken(),
                event.title(),
                preview(event.content()),
                data(event))));
    }

    private String preview(String content) {
        return content.length() <= BODY_LIMIT ? content : content.substring(0, BODY_LIMIT);
    }

    /** 탭하면 그 문의방으로 간다 (계약서 §7-1). 키 이름을 바꾸면 FE 딥링크가 깨진다. */
    private Map<String, Object> data(SupportPushEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", SupportPushEvent.TYPE);
        data.put("userId", event.roomUserId());
        return data;
    }
}
