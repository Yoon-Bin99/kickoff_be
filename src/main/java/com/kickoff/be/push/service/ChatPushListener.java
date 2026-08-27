package com.kickoff.be.push.service;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.client.PushProperties;
import com.kickoff.be.push.dto.ChatPushEvent;
import com.kickoff.be.push.dto.PushMessage;
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
 * 채팅 메시지를 푸시로 바꿔 보낸다 (계약서 §6-1·§8, v1.12.0).
 *
 * 규칙은 다른 리스너와 같다 — AFTER_COMMIT 이라 롤백된 메시지의 유령 알림이 나가지 않고,
 * @Async 라 Expo 왕복이 응답을 붙잡지 않으며, 어떤 예외도 밖으로 내지 않는다.
 * 계약서가 못박은 대로 <b>발송 실패가 메시지 저장을 실패시키면 안 된다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPushListener {

    /** 알림 본문에 싣는 길이 (계약서 §6-1). 잠금화면에 다 안 들어가기도 한다. */
    private static final int BODY_LIMIT = 50;

    private final UserRepository userRepository;
    private final PushClient pushClient;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ChatPushEvent event) {
        try {
            send(event);
        } catch (RuntimeException e) {
            log.warn("채팅 푸시 처리 실패 — requestId={}, userId={}",
                    event.requestId(), event.recipientUserId(), e);
        }
    }

    private void send(ChatPushEvent event) {
        if (!properties.enabled()) {
            log.debug("푸시 비활성 — 발송 생략 (requestId={})", event.requestId());
            return;
        }
        User recipient = userRepository.findById(event.recipientUserId()).orElse(null);
        if (recipient == null || !recipient.hasPushToken()) {
            log.debug("푸시 토큰 없음 — 발송 생략 (userId={})", event.recipientUserId());
            return;
        }

        pushClient.send(List.of(new PushMessage(
                recipient.getExpoPushToken(),
                // title 이 보낸 팀 이름이다 — 알림만 보고 누구와의 대화인지 알아야 한다
                event.senderTeamName(),
                preview(event.content()),
                data(event))));
    }

    /** 계약서가 "내용 앞 50자"라고만 정했다. 말줄임표를 붙이지 않은 건 그 문구 그대로다. */
    private String preview(String content) {
        return content.length() <= BODY_LIMIT ? content : content.substring(0, BODY_LIMIT);
    }

    /** 탭하면 그 매칭의 채팅방으로 간다 (계약서 §6-1). 키 이름을 바꾸면 딥링크가 깨진다. */
    private Map<String, Object> data(ChatPushEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", ChatPushEvent.TYPE);
        data.put("requestId", event.requestId());
        data.put("postId", event.postId());
        return data;
    }
}
