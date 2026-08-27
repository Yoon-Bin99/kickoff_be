package com.kickoff.be.push.dto;

/**
 * 채팅 메시지 알림 (계약서 §6-1·§8, v1.12.0).
 *
 * PushEventType 에 값을 더하지 않고 따로 둔 이유는 <b>title 이 고정 문구가 아니기</b>
 * 때문이다. 기존 열거형은 title 이 상수이고 body 만 포맷인데, 채팅은 title 이 보낸 팀
 * 이름이라 그 모양에 안 맞는다. 억지로 끼우면 "값마다 title 이 상수일 수도 아닐 수도
 * 있다"가 되어 다음 사람이 읽기 어려워진다.
 *
 * 다른 푸시와 같은 규칙은 그대로다 — 커밋 이후에 발송되므로 엔티티가 아니라 값만 싣고,
 * push token 은 발송 시점에 조회한다.
 */
public record ChatPushEvent(
        Long recipientUserId,
        Long requestId,
        Long postId,
        String senderTeamName,
        String content
) {

    /** 그대로 payload 의 {@code data.type} 이 된다. 바꾸면 FE 딥링크가 깨진다. */
    public static final String TYPE = "CHAT_MESSAGE";
}
