package com.kickoff.be.support.dto;

/**
 * FAQ 퀵버튼 한 개 (계약서 §7-1).
 *
 * 서버에 저장되지도, AI 를 부르지도 않는다 — FE 가 화면에서만 주고받은 것처럼 그린다.
 * 무료이고 즉답이라는 게 이 단계의 존재 이유다.
 */
public record FaqItem(int id, String question, String answer) {
}
