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

    /** 거절(4xx) 이 이만큼 쌓이면 더 보내지 않는다. {@link #markRejected} 참고. */
    public static final int DEAD_AFTER_ATTEMPTS = 5;

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

    /**
     * 더 보내지 않기로 한 시각 (V24). 채팅 서버가 "이건 못 받는다"고 답한 경우다.
     *
     * <b>지우지 않고 남긴다.</b> 7일 정리는 {@code deliveredAt} 을 보므로 dead 행은 자동으로
     * 빠진다 — 특히 PURGE 가 dead 면 탈퇴한 사람의 대화가 채팅 서버에 남아 있다는 뜻이라,
     * 행이 없으면 무엇을 지워야 하는지조차 알 수 없다.
     */
    @Column(name = "dead_at")
    private OffsetDateTime deadAt;

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

    /**
     * 채팅 서버가 "이건 못 받는다"고 답했다 — 모르는 type, 깨진 본문 (계약서 §10).
     *
     * 같은 것을 다시 보내면 같은 답이 온다. 그래도 바로 포기하지 않고 {@link #DEAD_AFTER_ATTEMPTS}
     * 번까지는 가 본다 — 배포가 엇갈려 <b>채팅 서버가 아직 이 type 을 모르는</b> 구간이 실제로
     * 있고(§10 전환 순서상 BE 가 먼저 올라간다), 그 구간은 몇 분이면 지나간다.
     *
     * <b>{@code attempts} 는 실패 종류를 가리지 않고 센다.</b> 5xx 로 세 번 실패한 뒤 400 을
     * 두 번 받으면 다섯 번째에서 dead 가 된다 — 400 만 다섯 번은 아니다. 그래도 맞다고 보는
     * 이유: 그 행은 어느 쪽이든 다섯 번 실패했고, 포기를 결정하는 것은 횟수가 아니라
     * <b>마지막 답이 "앞으로도 안 된다"였다는 사실</b>이다.
     *
     * @return 이번에 dead 가 됐으면 true (호출자가 그때만 ERROR 를 남긴다)
     */
    public boolean markRejected(OffsetDateTime now) {
        this.attempts++;
        if (this.attempts >= DEAD_AFTER_ATTEMPTS) {
            this.deadAt = now;
            return true;
        }
        this.nextAttemptAt = now.plus(backoff(this.attempts));
        return false;
    }

    public boolean isDead() {
        return this.deadAt != null;
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
