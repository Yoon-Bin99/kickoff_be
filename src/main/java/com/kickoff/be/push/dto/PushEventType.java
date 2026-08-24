package com.kickoff.be.push.dto;

/**
 * 알림 이벤트 4종 (계약서 §8). 이 값이 그대로 푸시 payload 의 {@code data.type} 으로 나가고,
 * FE 는 그걸 보고 어느 화면으로 이동할지 정한다 — 문자열을 바꾸면 딥링크가 깨진다.
 *
 * title/body 문구도 계약서 표에 규정돼 있어 여기 함께 둔다. 문구가 코드 여기저기 흩어지면
 * 계약과 대조할 자리가 없어진다.
 */
public enum PushEventType {

    /** 신청이 들어왔다 — 글 작성 팀에게. */
    REQUEST_RECEIVED("새 매칭 신청", "%s이(가) '%s'에 신청했습니다"),

    /** 수락됐다 — 신청 팀에게. */
    REQUEST_ACCEPTED("매칭 성사!", "'%s' 신청이 수락됐습니다. 연락처가 공개됐어요"),

    /** 거절됐다 — 신청 팀에게. 수락 시 자동 거절되는 신청들에도 각각 나간다. */
    REQUEST_REJECTED("매칭 불발", "'%s' 신청이 거절됐습니다"),

    /** 입금이 확인됐다 — 신청 팀에게. */
    DEPOSIT_CONFIRMED("입금 확인", "'%s' 입금이 확인됐습니다");

    private final String title;
    private final String bodyFormat;

    PushEventType(String title, String bodyFormat) {
        this.title = title;
        this.bodyFormat = bodyFormat;
    }

    public String title() {
        return title;
    }

    /**
     * REQUEST_RECEIVED 만 신청 팀 이름이 앞에 붙고 나머지는 글 제목 하나뿐이라 인자 수가 다르다.
     * 포맷 문자열이 쓰지 않는 인자는 그냥 무시되므로 호출부는 둘 다 넘기면 된다.
     */
    public String body(String applicantTeamName, String postTitle) {
        return this == REQUEST_RECEIVED
                ? bodyFormat.formatted(applicantTeamName, postTitle)
                : bodyFormat.formatted(postTitle);
    }
}
