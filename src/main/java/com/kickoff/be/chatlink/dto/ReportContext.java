package com.kickoff.be.chatlink.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 채팅 서버가 주는 신고 문맥 (계약서 §10 report-context, v1.35.0).
 *
 * <b>신고자에게 보이던 것만</b> 담겨 온다 — 신고자 팀이 방을 나간 적이 있으면 그 이전은
 * 빠진 채로 온다(§6-1 나가기 규칙). 여기서 더 거르지 않는다.
 *
 * @param reportedMessage 신고된 그 메시지
 * @param messages        신고된 메시지와 그 앞뒤, 시간 오름차순
 */
public record ReportContext(InternalChatMessage reportedMessage,
                            List<InternalChatMessage> messages) {

    /**
     * §6-1 {@code ChatMessage} + {@code senderUserId}.
     *
     * 보낸 사람의 <b>이름</b>은 오지 않는다 — 채팅 서버는 팀 이름을 들고 있지 않다
     * (방 목록의 팀 정보는 BE 의 {@code RoomContext} 에서 간다). 사본에는 id 만 남기고,
     * 운영자 화면이 지금 이름을 붙인다.
     */
    public record InternalChatMessage(Long id, Long requestId, String type, Long senderTeamId,
                                      Long senderUserId, String content,
                                      OffsetDateTime createdAt) {
    }
}
