package com.kickoff.be.chatlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 채팅 서버가 "이 사람에게 푸시를 보내 달라"고 부르는 본문 (계약서 §10).
 *
 * <b>BE 는 받을지 말지를 판단하지 않는다.</b> 상대가 방을 나갔는지, 지금 소켓으로 구독
 * 중인지는 채팅 서버만 아는 사실이라 그쪽이 정한 뒤 부른다. 여기서 또 보면 규칙이 두 곳에
 * 생기고, BE 쪽 판단은 어차피 옛 데이터를 보게 된다.
 *
 * 길이 제한은 계약서 §6-1 의 메시지 규칙(1~500자)과 같은 값이다 — 같은 내용이 오므로
 * 다르게 잡을 이유가 없다. §10 은 "검증 실패는 400 VALIDATION_FAILED" 라고만 정했다.
 */
public record ChatPushRequest(

        @NotNull(message = "recipientUserId 는 필수입니다.")
        Long recipientUserId,

        @NotNull(message = "requestId 는 필수입니다.")
        Long requestId,

        @NotNull(message = "postId 는 필수입니다.")
        Long postId,

        /** 알림 제목이 된다 — 알림만 보고 누구와의 대화인지 알아야 한다 (§6-1). */
        @NotBlank(message = "senderTeamName 은 필수입니다.")
        @Size(max = 30, message = "senderTeamName 은 30자를 넘을 수 없습니다.")
        String senderTeamName,

        /** 알림 본문은 이 값의 앞 50자다 (§6-1). */
        @NotBlank(message = "content 는 필수입니다.")
        @Size(max = 500, message = "content 는 500자를 넘을 수 없습니다.")
        String content
) {
}
