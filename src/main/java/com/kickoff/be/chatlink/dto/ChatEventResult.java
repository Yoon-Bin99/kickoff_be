package com.kickoff.be.chatlink.dto;

/**
 * 이벤트 한 건을 보낸 결과 (계약서 §10).
 *
 * @param outcome 다음에 무엇을 할지
 * @param status  받은 HTTP 상태. <b>연결조차 못 했으면 null 이다</b> — 로그에 "상태 없음"과
 *                "502" 가 구별되어야 원인이 네트워크인지 채팅 서버인지 갈린다
 */
public record ChatEventResult(ChatEventOutcome outcome, Integer status) {

    public static ChatEventResult delivered(int status) {
        return new ChatEventResult(ChatEventOutcome.DELIVERED, status);
    }

    /** 연결 실패·타임아웃 — 응답 자체가 없다. */
    public static ChatEventResult noResponse() {
        return new ChatEventResult(ChatEventOutcome.RETRY, null);
    }

    public static ChatEventResult of(ChatEventOutcome outcome, int status) {
        return new ChatEventResult(outcome, status);
    }

    public boolean is(ChatEventOutcome other) {
        return this.outcome == other;
    }

    /** 로그용. 응답이 없었으면 숫자 대신 그렇게 적는다. */
    public String describeStatus() {
        return status == null ? "응답 없음" : String.valueOf(status);
    }
}
