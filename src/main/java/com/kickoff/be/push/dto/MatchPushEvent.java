package com.kickoff.be.push.dto;

/**
 * 매칭 이벤트가 났다는 사실만 담는다. 실제 발송은 트랜잭션이 커밋된 뒤에 일어나므로,
 * <b>엔티티가 아니라 값만</b> 실어 보낸다 — 커밋 이후에 지연 로딩을 건드리면 세션이 없어
 * 터진다.
 *
 * push token 은 일부러 담지 않는다. 발송 시점에 조회해야 그 사이에 바뀐 토큰(기기 교체,
 * 로그아웃)이 반영된다.
 *
 * @param recipientUserId 알림을 받을 사용자. 계약서상 수신자는 언제나 팀의 소유자다
 */
public record MatchPushEvent(
        PushEventType type,
        Long recipientUserId,
        Long requestId,
        Long postId,
        String postTitle,
        String applicantTeamName
) {
}
