package com.kickoff.be.chatlink.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 이벤트 대기열의 운영 설정 (계약서 §10, 법률 점검 11절).
 *
 * @param purgeAlertAfter 미전송 {@code PURGE} 가 이만큼 묵으면 ERROR 로 알린다. 기본 1시간.
 *                        <b>PURGE 만인 이유</b>: ROOM_INVALIDATED 가 늦는 것은 채팅 서버의
 *                        15초 캐시가 메워 주지만, PURGE 가 늦는 것은 탈퇴한 사람의 대화가
 *                        아직 안 지워졌다는 뜻이다. 무한 재시도라 그냥 두면 계속 실패해도
 *                        아무도 모른다 — 그 침묵을 깨는 것이 이 값의 목적이다.
 */
@ConfigurationProperties(prefix = "chat.outbox")
public record ChatOutboxProperties(Duration purgeAlertAfter) {

    private static final Duration DEFAULT_PURGE_ALERT_AFTER = Duration.ofHours(1);

    public ChatOutboxProperties {
        // 설정을 빠뜨린 쪽이 "경보 없음"이 되면 안 된다 — 빠뜨리기 쉬운 값일수록 기본이
        // 켜져 있어야 한다.
        purgeAlertAfter = purgeAlertAfter == null ? DEFAULT_PURGE_ALERT_AFTER : purgeAlertAfter;
    }
}
