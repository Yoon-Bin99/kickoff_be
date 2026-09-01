package com.kickoff.be.push.dto;

/**
 * 고객센터 문의 알림 (계약서 §7-1·§8, v1.22.0).
 *
 * {@link ChatPushEvent} 와 같은 이유로 {@link PushEventType} 에 넣지 않는다 —
 * <b>title 이 방향에 따라 다르다.</b> 사용자가 보내면 운영자에게 "고객센터 문의",
 * 운영자가 답하면 그 사용자에게 "킥오프 고객센터"가 뜬다. 열거형은 title 이 상수인
 * 모양이라 억지로 끼우면 "값마다 상수일 수도 아닐 수도 있다"가 된다.
 *
 * {@code roomUserId} 는 <b>방 주인</b>이지 수신자가 아니다. 운영자가 탭했을 때 어느 방을
 * 열지 정하는 값이라, 운영자에게 가는 알림에서는 수신자와 다른 사람을 가리킨다.
 */
public record SupportPushEvent(
        Long recipientUserId,
        Long roomUserId,
        String title,
        String content
) {

    /** 그대로 payload 의 {@code data.type} 이 된다. 바꾸면 FE 딥링크가 깨진다. */
    public static final String TYPE = "SUPPORT_MESSAGE";

    /** 사용자 → 운영자. title 에 보낸 사람 닉네임을 앞에 붙인다 (계약서 §7-1 표). */
    public static SupportPushEvent toOperator(Long operatorUserId, Long roomUserId,
                                              String nickname, String content) {
        return new SupportPushEvent(operatorUserId, roomUserId, "고객센터 문의",
                nickname + ": " + content);
    }

    /** 운영자 → 사용자. 사용자에게 운영자 계정은 보이지 않는다 (계약서 §7-1). */
    public static SupportPushEvent toUser(Long userId, String content) {
        return new SupportPushEvent(userId, userId, "킥오프 고객센터", content);
    }
}
