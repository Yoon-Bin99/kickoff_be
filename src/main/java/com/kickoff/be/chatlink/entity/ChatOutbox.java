package com.kickoff.be.chatlink.entity;

import com.kickoff.be.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채팅 서버로 보낼 이벤트 한 건 (계약서 §10, v1.31.0).
 *
 * 원 동작(취소·탈퇴 등)과 <b>같은 트랜잭션에서</b> 저장되고, 보내는 일은 스케줄러가 따로
 * 한다. 채팅 서버가 꺼져 있어도 원 동작은 성공해야 하기 때문이다.
 *
 * <b>{@code id} 가 곧 계약서의 {@code eventId} 다.</b> 재시도해도 같은 값이 나가므로 채팅
 * 서버가 멱등하게 처리할 수 있다 — 전달 보장은 "최소 한 번"이다.
 */
@Entity
@Getter
@Table(name = "chat_outbox", indexes = {
        @Index(name = "idx_chat_outbox_pending", columnList = "delivered_at, next_attempt_at, id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatOutbox extends BaseTimeEntity {

    /**
     * 한 행에 담는 id 개수 상한.
     *
     * 칸이 varchar(4000) 이라 최악(19자리 id)으로도 넘치지 않는 수다. 넘치면 insert 가
     * 실패하고 <b>그 실패가 원 트랜잭션(탈퇴)을 통째로 되돌린다</b> — 채팅 뒷정리 때문에
     * 사용자가 탈퇴를 못 하는 일만은 없어야 해서 여유 있게 잡았다. 기록하는 쪽이 이 수로
     * 쪼개 여러 행을 넣는다.
     */
    public static final int MAX_IDS_PER_ROW = 150;

    /** 첫 재시도까지의 간격. 이후 2배씩 늘려 {@link #MAX_BACKOFF} 에서 멈춘다. */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);

    /** 계약서 §10 이 정한 상한. 채팅 서버가 복구되면 늦어도 5분 안에 다시 두드린다. */
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(5);

    private static final String SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ChatEventType eventType;

    /** 쉼표로 이은 숫자. 빈 목록은 빈 문자열이다 (null 과 구분할 이유가 없다). */
    @Column(name = "request_ids", nullable = false, length = 4000)
    private String requestIds;

    @Column(name = "team_ids", nullable = false, length = 4000)
    private String teamIds;

    /** 보내려고 시도한 횟수. 백오프 간격을 여기서 센다. */
    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    /** null 이면 미전송이다. 상태 칸을 따로 두지 않은 이유는 마이그레이션 주석에 적었다. */
    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    private ChatOutbox(ChatEventType eventType, Collection<Long> requestIds,
                       Collection<Long> teamIds, OffsetDateTime now) {
        this.eventType = eventType;
        this.requestIds = join(requestIds);
        this.teamIds = join(teamIds);
        this.attempts = 0;
        // 바로 보낼 수 있다. 스케줄러가 다음 바퀴에 집어 간다.
        this.nextAttemptAt = now;
    }

    public static ChatOutbox roomInvalidated(Collection<Long> requestIds, OffsetDateTime now) {
        return new ChatOutbox(ChatEventType.ROOM_INVALIDATED, requestIds, List.of(), now);
    }

    public static ChatOutbox purge(Collection<Long> requestIds, Collection<Long> teamIds,
                                   OffsetDateTime now) {
        return new ChatOutbox(ChatEventType.PURGE, requestIds, teamIds, now);
    }

    public List<Long> requestIdList() {
        return parse(this.requestIds);
    }

    public List<Long> teamIdList() {
        return parse(this.teamIds);
    }

    public void markDelivered(OffsetDateTime now) {
        this.deliveredAt = now;
    }

    /**
     * 실패를 기록하고 다음 시도 시각을 미룬다 (계약서 §10 "지수 백오프, 최대 5분").
     *
     * 시도 횟수에 상한을 두지 않는다 — 버리면 채팅 서버가 <b>영영 모르는</b> 방이 남는데,
     * 그건 지워졌어야 할 대화가 남아 있는 상태다. 보낼 곳이 살아날 때까지 기다리는 편이 낫다.
     */
    public void markFailed(OffsetDateTime now) {
        this.attempts++;
        this.nextAttemptAt = now.plus(backoff(this.attempts));
    }

    static Duration backoff(int attempts) {
        if (attempts <= 0) {
            return BASE_BACKOFF;
        }
        // 1 << 30 을 넘기면 long 이 돌아 음수가 된다. 어차피 상한에 걸리는 구간이라
        // 지수를 먼저 끊는다.
        int exponent = Math.min(attempts - 1, 30);
        Duration grown = BASE_BACKOFF.multipliedBy(1L << exponent);
        return grown.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : grown;
    }

    private static String join(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (Long id : ids) {
            if (!text.isEmpty()) {
                text.append(SEPARATOR);
            }
            text.append(id);
        }
        return text.toString();
    }

    private static List<Long> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(SEPARATOR))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
