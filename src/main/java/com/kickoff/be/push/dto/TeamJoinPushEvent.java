package com.kickoff.be.push.dto;

/**
 * 팀 가입 이벤트 (계약서 §4-3·§8, v1.11.0).
 *
 * MatchPushEvent 와 같은 규칙이다 — 커밋 이후에 발송되므로 <b>엔티티가 아니라 값만</b>
 * 싣는다. push token 도 담지 않는다. 발송 시점에 조회해야 그 사이 바뀐 토큰이 반영된다.
 *
 * data 에는 teamId 만 나간다 (계약서 §4-3). 세 이벤트 모두 탭하면 팀 페이지로 간다.
 */
public record TeamJoinPushEvent(
        TeamJoinPushType type,
        Long recipientUserId,
        Long teamId,
        String teamName,
        String applicantNickname
) {
}
