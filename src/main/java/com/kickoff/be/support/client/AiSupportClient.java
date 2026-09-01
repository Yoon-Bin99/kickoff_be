package com.kickoff.be.support.client;

import java.util.List;

/**
 * AI 상담사 (계약서 §7-1, v1.22.0).
 *
 * 어댑터를 인터페이스로 뺀 이유는 문자 발송과 같다 — 테스트가 실제 제공자를 부르지
 * 않아야 하고, 제공자 실패가 우리 에러 코드로 번역돼야 한다.
 */
public interface AiSupportClient {

    /**
     * 답변을 만든다. 실패하면 {@link AiUnavailableException} 을 던진다 —
     * 호출자는 그걸 받아 <b>강등 안내</b>로 바꾼다 (계약서 §7-1).
     *
     * @param systemPrompt 역할·하드 제약·서비스 지식
     * @param history      오래된 것부터. 마지막이 방금 사용자가 보낸 말이다.
     */
    String reply(String systemPrompt, List<AiTurn> history);

    /** 대화 한 턴. 운영자 말도 assistant 쪽으로 넘긴다 — AI 에게는 "이미 한 답"이다. */
    record AiTurn(boolean fromUser, String content) {
    }

    /** 제공자 호출 실패. 타임아웃·인증 실패·크레딧 부족이 전부 여기로 모인다. */
    class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
