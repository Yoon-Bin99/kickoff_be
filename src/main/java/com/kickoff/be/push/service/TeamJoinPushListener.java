package com.kickoff.be.push.service;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.client.PushProperties;
import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.push.dto.TeamJoinPushEvent;
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
 * 팀 가입 이벤트를 푸시로 바꿔 보낸다 (계약서 §4-3·§8, v1.11.0).
 *
 * 규칙은 MatchPushListener 와 같다 — AFTER_COMMIT 이라 롤백된 신청에 유령 알림이 나가지
 * 않고, @Async 라 Expo 왕복이 응답을 붙잡지 않으며, 어떤 예외도 밖으로 내지 않는다.
 * 알림이 실패했다고 이미 승인된 가입을 무를 수는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TeamJoinPushListener {

    private final UserRepository userRepository;
    private final PushClient pushClient;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamJoinPushEvent event) {
        try {
            send(event);
        } catch (RuntimeException e) {
            log.warn("가입 푸시 처리 실패 — type={}, userId={}",
                    event.type(), event.recipientUserId(), e);
        }
    }

    private void send(TeamJoinPushEvent event) {
        if (!properties.enabled()) {
            log.debug("푸시 비활성 — 발송 생략 (type={})", event.type());
            return;
        }
        User recipient = userRepository.findById(event.recipientUserId()).orElse(null);
        if (recipient == null || !recipient.hasPushToken()) {
            log.debug("푸시 토큰 없음 — 발송 생략 (userId={})", event.recipientUserId());
            return;
        }

        pushClient.send(List.of(new PushMessage(
                recipient.getExpoPushToken(),
                event.type().title(),
                event.type().body(event.applicantNickname(), event.teamName()),
                data(event))));
    }

    /** 세 이벤트 모두 팀 페이지로 간다 (계약서 §4-3). 키 이름을 바꾸면 딥링크가 깨진다. */
    private Map<String, Object> data(TeamJoinPushEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.type().name());
        data.put("teamId", event.teamId());
        return data;
    }
}
