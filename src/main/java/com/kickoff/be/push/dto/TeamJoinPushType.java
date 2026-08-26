package com.kickoff.be.push.dto;

/**
 * 팀 가입 알림 3종 (계약서 §4-3·§8, v1.11.0).
 *
 * PushEventType 과 따로 둔다. 저쪽 data 에는 requestId·postId 가 실리고 이쪽은 teamId 라
 * payload 모양이 다르다 — 한 열거형에 섞으면 어떤 값이 실리는지가 값마다 달라진다.
 *
 * 이 이름이 그대로 payload 의 {@code data.type} 으로 나가므로 문자열을 바꾸면 FE 딥링크가
 * 깨진다. 문구도 계약서 표 그대로다.
 */
public enum TeamJoinPushType {

    /** 가입 신청이 들어왔다 — 팀 소유자에게. */
    JOIN_REQUEST_RECEIVED("가입 신청", "%s님이 %s에 가입 신청했습니다"),

    /** 수락됐다 — 신청자에게. */
    JOIN_ACCEPTED("가입 승인!", "%s의 멤버가 됐습니다"),

    /** 거절됐다 — 신청자에게. */
    JOIN_REJECTED("가입 불발", "%s 가입 신청이 거절됐습니다");

    private final String title;
    private final String bodyFormat;

    TeamJoinPushType(String title, String bodyFormat) {
        this.title = title;
        this.bodyFormat = bodyFormat;
    }

    public String title() {
        return title;
    }

    /** JOIN_REQUEST_RECEIVED 만 신청자 닉네임이 앞에 붙고 나머지는 팀 이름 하나다. */
    public String body(String applicantNickname, String teamName) {
        return this == JOIN_REQUEST_RECEIVED
                ? bodyFormat.formatted(applicantNickname, teamName)
                : bodyFormat.formatted(teamName);
    }
}
