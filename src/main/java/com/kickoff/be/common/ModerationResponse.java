package com.kickoff.be.common;

import java.time.OffsetDateTime;

/**
 * 응답에 실리는 운영 조치 (계약서 §7-2 — {@code moderation}, v1.35.0).
 *
 * 조치가 없거나 기한이 지났으면 <b>필드 자체가 null</b> 이다. FE 는 이 값이 있을 때만
 * 가림 표시를 그린다.
 *
 * @param status {@code HIDDEN} 이면 임시 가림, {@code REMOVED} 면 삭제
 * @param until  {@code HIDDEN} 의 기한. {@code REMOVED} 면 null
 */
public record ModerationResponse(ModerationStatus status, OffsetDateTime until) {

    /**
     * 응답을 만드는 자리에서는 "지금" 을 따로 받지 않는다.
     *
     * 가려졌는지는 <b>그리는 순간</b>의 질문이고, 서비스가 들고 있는 now 와 몇 밀리초
     * 달라도 답이 달라질 자리가 아니다. 판정(410 을 낼지, 목록에서 뺄지)은 서비스가
     * 자기 now 로 한다 — 그쪽은 한 요청 안에서 일관돼야 한다.
     */
    public static ModerationResponse of(Moderation moderation) {
        return of(moderation, OffsetDateTime.now());
    }

    public static ModerationResponse of(Moderation moderation, OffsetDateTime now) {
        if (moderation == null || !moderation.isActiveAt(now)) {
            return null;
        }
        return new ModerationResponse(moderation.getStatus(), moderation.getUntil());
    }

    /**
     * 가려진 콘텐츠를 열려고 할 때 보여 줄 문구 (계약서 §7-2).
     *
     * 410 의 {@code message} 이자 리뷰 자리 표시의 문구다. <b>조치 사실을 그 자리에 남기는
     * 것이 법이 요구하는 바</b>라서(정보통신망법 제44조의2 제2항), 조용히 없애거나 404 로
     * 답하지 않는다.
     */
    public static String messageFor(ModerationStatus status, boolean review) {
        String what = review ? "리뷰" : "게시물";
        return status == ModerationStatus.REMOVED
                ? "운영 정책에 따라 삭제된 %s입니다".formatted(what)
                : "권리침해 신고로 임시로 가려진 %s입니다".formatted(what);
    }
}
