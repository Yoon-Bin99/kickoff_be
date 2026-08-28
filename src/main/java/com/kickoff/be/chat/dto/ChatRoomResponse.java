package com.kickoff.be.chat.dto;

import com.kickoff.be.team.dto.TeamSummary;
import java.time.OffsetDateTime;

/**
 * 채팅 탭의 방 한 줄 (계약서 §6-1, v1.13.0).
 *
 * {@code lastMessage} 는 <b>내게 보이는</b> 마지막 메시지다 — 나가기 워터마크 이전은 세지
 * 않는다. 메시지가 없으면 null 이고, 그래도 방은 목록에 남는다: 수락 직후의 빈 방을 숨기면
 * 조율을 시작하라는 신호 자체가 사라진다.
 */
public record ChatRoomResponse(
        Long requestId,
        Long postId,
        String postTitle,
        OffsetDateTime matchAt,
        boolean chatOpen,
        TeamSummary otherTeam,
        ChatMessageResponse lastMessage
) {
}
