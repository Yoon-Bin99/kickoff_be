package com.kickoff.be.push.service;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.client.PushProperties;
import com.kickoff.be.push.dto.MatchPushEvent;
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
 * 매칭 이벤트를 푸시로 바꿔 보낸다 (계약서 §8).
 *
 * <b>AFTER_COMMIT</b> 이라 원 트랜잭션이 커밋된 뒤에만 실행된다. 커밋 전에 보내면 롤백된
 * 신청에 대한 유령 알림이 나간다. <b>@Async</b> 라 Expo 왕복이 사용자 응답을 붙잡지 않는다.
 *
 * 그리고 <b>어떤 예외도 밖으로 내지 않는다.</b> 푸시는 best-effort 다 — 알림이 실패했다고
 * 이미 성사된 매칭을 무를 수는 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchPushListener {

    private final UserRepository userRepository;
    private final PushClient pushClient;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchPushEvent event) {
        try {
            send(event);
        } catch (RuntimeException e) {
            // 여기서 터져도 원 요청은 이미 커밋됐고 응답도 나갔다. 로그만 남긴다.
            log.warn("푸시 처리 실패 — type={}, userId={}", event.type(), event.recipientUserId(), e);
        }
    }

    private void send(MatchPushEvent event) {
        if (!properties.enabled()) {
            log.debug("푸시 비활성 — 발송 생략 (type={})", event.type());
            return;
        }
        User recipient = userRepository.findById(event.recipientUserId()).orElse(null);
        if (recipient == null || !recipient.hasPushToken()) {
            // 토큰을 등록하지 않은 사용자다. 계약서대로 조용히 건너뛴다.
            log.debug("푸시 토큰 없음 — 발송 생략 (userId={})", event.recipientUserId());
            return;
        }

        pushClient.send(List.of(new PushMessage(
                recipient.getExpoPushToken(),
                event.type().title(),
                event.type().body(event.applicantTeamName(), event.postTitle()),
                data(event))));
    }

    /** FE 가 알림을 탭했을 때 어디로 갈지 정하는 값 (계약서 §8). 키 이름을 바꾸면 딥링크가 깨진다. */
    private Map<String, Object> data(MatchPushEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", event.type().name());
        data.put("requestId", event.requestId());
        data.put("postId", event.postId());
        return data;
    }
}
