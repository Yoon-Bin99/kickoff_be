package com.kickoff.be.common;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 콘텐츠에 걸린 운영 조치 (계약서 §7-2 「가려진 콘텐츠」, v1.35.0).
 *
 * 모집글·리뷰·팀 소개가 같은 모양을 쓰므로 한 곳에 둔다. 콘텐츠의 속성이라 별도 표로 빼지
 * 않았다 — 목록에서 가려진 것을 빼는 일이 <b>모든 조회의 where 절</b>이라서, 표를 나누면
 * 조회마다 조인이 붙고 빠뜨리는 자리가 생긴다.
 *
 * <b>기한은 읽을 때 본다.</b> {@code HIDDEN} 이 지나면 자동으로 풀려야 하는데, 그것을
 * 배치로 지우면 배치가 도는 시각까지 늦게 풀린다(그리고 안 도는 날이 있다). 조회마다
 * "지금" 을 넘겨 판정하면 늦을 일이 없다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Moderation {

    /** 임시 가림 기간 (계약서 §7-2 — 정보통신망법 제44조의2 제4항의 임시조치 기간). */
    public static final int HIDE_DAYS = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20)
    private ModerationStatus status;

    /** {@code HIDDEN} 의 기한. {@code REMOVED} 면 null 이다. */
    @Column(name = "moderation_until")
    private OffsetDateTime until;

    private Moderation(ModerationStatus status, OffsetDateTime until) {
        this.status = status;
        this.until = until;
    }

    /** 지금부터 30일 동안 가린다. */
    public static Moderation hiddenFrom(OffsetDateTime now) {
        return new Moderation(ModerationStatus.HIDDEN, now.plus(HIDE_DAYS, ChronoUnit.DAYS));
    }

    public static Moderation removed() {
        return new Moderation(ModerationStatus.REMOVED, null);
    }

    /**
     * 지금 이 조치가 살아 있는지.
     *
     * 경계는 <b>기한이 지나면 풀린다</b>다 — {@code until} 과 같은 순간에는 아직 가려져
     * 있다(§5 의 표시 기간, §6-1 의 보관 기간과 같은 방향이다).
     */
    public boolean isActiveAt(OffsetDateTime now) {
        if (status == null) {
            return false;
        }
        if (status == ModerationStatus.REMOVED) {
            return true;
        }
        return until != null && !now.isAfter(until);
    }

    /** 조치가 아예 걸린 적 없는 상태. 엔티티의 필드를 null 로 두지 않기 위한 값이다. */
    public static Moderation none() {
        return new Moderation(null, null);
    }

    public boolean isRemoved() {
        return status == ModerationStatus.REMOVED;
    }
}
