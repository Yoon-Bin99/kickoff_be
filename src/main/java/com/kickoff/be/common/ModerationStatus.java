package com.kickoff.be.common;

/**
 * 운영 조치 상태 (계약서 §1·§7-2, v1.35.0).
 *
 * 두 값의 차이는 <b>되돌아오는가</b>다. {@code HIDDEN} 은 기한이 지나면 저절로 풀린다 —
 * 운영자가 30일 안에 삭제·복원을 정하지 못하면 다시 보이는 쪽이 맞다(정보통신망법
 * 제44조의2 제4항의 임시조치 기간이 30일이다). {@code REMOVED} 는 기한이 없다.
 */
public enum ModerationStatus {

    /** 임시 가림. 기한({@code until})이 있고, 지나면 자동으로 풀린다. */
    HIDDEN,

    /** 운영자 삭제. 기한이 없다. */
    REMOVED
}
