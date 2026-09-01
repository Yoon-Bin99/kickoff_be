package com.kickoff.be.support.dto;

import java.util.List;

/**
 * 계약서 §7-1. 매칭 채팅과 달리 {@code chatOpen} 이 없다 — 문의방은 항상 열려 있다.
 *
 * <b>{@code operatorMode} 는 서버가 방 상태의 진실이라는 뜻이다</b> (v1.22.0 확정판).
 * FE 가 말풍선으로 추론하면 <b>escalate 직후 무응답 구간</b>이 안 보인다 — 운영자를
 * 불렀지만 아직 답이 없는 그 구간이 정상 경로이고, 화면에는 여전히 "운영자 연결하기"가
 * 떠 있게 된다. 사용자는 이미 부른 버튼을 또 누른다.
 */
public record SupportChatResponse(boolean operatorMode, List<SupportMessageResponse> messages) {
}
