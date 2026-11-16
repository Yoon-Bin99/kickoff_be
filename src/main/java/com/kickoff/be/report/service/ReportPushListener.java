package com.kickoff.be.report.service;

import com.kickoff.be.push.client.PushClient;
import com.kickoff.be.push.client.PushProperties;
import com.kickoff.be.push.dto.PushMessage;
import com.kickoff.be.support.service.OperatorAccounts;
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
 * 신고가 들어오면 운영자에게 알린다 (계약서 §7-2 「신고의 즉시 효과」 3).
 *
 * 다른 리스너와 같은 규칙이다 — AFTER_COMMIT 이라 롤백된 신고의 유령 알림이 나가지 않고,
 * {@code @Async} 라 Expo 왕복이 응답을 붙잡지 않으며, 어떤 예외도 밖으로 내지 않는다.
 * <b>알림 실패가 신고 접수를 실패시키면 안 된다.</b>
 *
 * 운영자가 설정돼 있지 않으면 조용히 넘어간다 — 그래도 신고는 쌓이고, 운영자 계정이
 * 생기면 그때부터 목록에서 보인다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportPushListener {

    private final OperatorAccounts operatorAccounts;
    private final UserRepository userRepository;
    private final PushClient pushClient;
    private final PushProperties properties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportReceivedEvent event) {
        try {
            send(event);
        } catch (RuntimeException e) {
            log.warn("신고 접수 푸시 실패 — reportId={}", event.reportId(), e);
        }
    }

    private void send(ReportReceivedEvent event) {
        if (!properties.enabled()) {
            return;
        }
        Long operatorId = operatorAccounts.operatorUserId().orElse(null);
        if (operatorId == null) {
            log.debug("운영자 계정 없음 — 신고 알림 생략 (reportId={})", event.reportId());
            return;
        }
        User operator = userRepository.findById(operatorId).orElse(null);
        if (operator == null || !operator.hasPushToken()) {
            return;
        }
        pushClient.send(List.of(new PushMessage(
                operator.getExpoPushToken(),
                "신고 접수",
                "%s · %s".formatted(event.reasonName(), event.targetName()),
                data(event))));
    }

    /** 탭하면 그 신고로 간다 (계약서 §7-2). 키 이름을 바꾸면 FE 딥링크가 깨진다. */
    private Map<String, Object> data(ReportReceivedEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", ReportReceivedEvent.TYPE);
        data.put("reportId", event.reportId());
        return data;
    }
}
