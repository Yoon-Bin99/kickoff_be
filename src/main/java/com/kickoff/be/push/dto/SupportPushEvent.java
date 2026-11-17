package com.kickoff.be.push.dto;

/**
 * 고객센터 알림 (계약서 §7-1·§8, v1.22.0).
 *
 * {@link ChatPushEvent} 와 같은 이유로 {@link PushEventType} 에 넣지 않는다 —
 * <b>title 이 방향에 따라 다르다.</b> 사용자가 보내면 운영자에게 "고객센터 문의",
 * 운영자가 답하면 그 사용자에게 "킥오프 고객센터"가 뜬다. 열거형은 title 이 상수인
 * 모양이라 억지로 끼우면 "값마다 상수일 수도 아닐 수도 있다"가 된다.
 *
 * <b>본문에 메시지 내용을 싣지 않는다</b> (v1.38.3). 세 가지가 겹쳐서다.
 * ① 이 경로로 나가는 통지에는 신고 처리 결과·임시 가림·이용 제한이 들어 있다 —
 * 잠금 화면에 제재 사실이 뜨고, 신고자를 숨겨 온 설계와 어긋난다.
 * ② 문의 내용은 이용자가 자유롭게 적는 글이라 전화번호·계좌가 섞여 들어올 수 있다.
 * ③ 푸시는 국외 업체(Expo)를 거친다. 내용은 앱 안에서만 본다.
 *
 * {@code roomUserId} 는 <b>방 주인</b>이지 수신자가 아니다. 운영자가 탭했을 때 어느 방을
 * 열지 정하는 값이라, 운영자에게 가는 알림에서는 수신자와 다른 사람을 가리킨다.
 */
public record SupportPushEvent(
        Long recipientUserId,
        Long roomUserId,
        String title,
        String body
) {

    /** 그대로 payload 의 {@code data.type} 이 된다. 바꾸면 FE 딥링크가 깨진다. */
    public static final String TYPE = "SUPPORT_MESSAGE";

    /**
     * 사용자 → 운영자. 닉네임까지만 적는다 — 누구의 방을 열어야 하는지가 운영자에게
     * 필요한 전부이고, 문의 내용은 그 방에서 본다.
     */
    public static SupportPushEvent toOperator(Long operatorUserId, Long roomUserId,
                                              String nickname) {
        return new SupportPushEvent(operatorUserId, roomUserId, "고객센터 문의",
                nickname + "님의 새 문의가 있습니다");
    }

    /** 운영자 → 사용자. 사용자에게 운영자 계정은 보이지 않는다 (계약서 §7-1). */
    public static SupportPushEvent toUser(Long userId) {
        return new SupportPushEvent(userId, userId, "킥오프 고객센터",
                "문의에 답변이 도착했습니다");
    }

    /**
     * 서버가 넣는 통지 — 신고 처리 결과·임시 가림·삭제·이용 제한 (계약서 §7-2).
     *
     * <b>답변과 문구를 따로 둔다.</b> 통지는 이용자가 먼저 물어본 것이 아니라서
     * "답변이 도착했습니다"가 맞지 않고, 무엇에 대한 통지인지는 더더욱 적지 않는다.
     */
    public static SupportPushEvent notice(Long userId) {
        return new SupportPushEvent(userId, userId, "킥오프 고객센터",
                "새 알림이 있습니다. 앱에서 확인해 주세요");
    }
}
