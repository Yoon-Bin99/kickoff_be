package com.kickoff.be.report.dto;

/**
 * 신고 접수 응답 (계약서 §7-2 — 201 {@code { "id": 31 }}).
 *
 * 처리 결과는 고객센터 메시지와 푸시로 간다. <b>신고 내역을 조회하는 사용자 API 는 없다</b>
 * — 내 신고 목록을 열어 두면 "누가 무엇을 신고했는지"가 쌓인 화면이 생긴다.
 */
public record ReportCreatedResponse(Long id) {
}
