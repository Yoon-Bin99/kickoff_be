package com.kickoff.be.operator.dto;

import java.time.OffsetDateTime;

/**
 * 운영자 세션 발급 응답 (계약서 §7-2, v1.35.1).
 *
 * FE 는 이 토큰을 <b>메모리에만</b> 둔다 — 저장소에 남기면 기기를 잃었을 때 30분이 아니라
 * 영영 열려 있는 열쇠가 된다.
 */
public record OperatorSessionResponse(String operatorToken, OffsetDateTime expiresAt) {
}
