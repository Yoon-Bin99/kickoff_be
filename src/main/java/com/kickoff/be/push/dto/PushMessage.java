package com.kickoff.be.push.dto;

import java.util.Map;

/**
 * Expo Push API 가 받는 한 건의 메시지. 필드 이름이 그대로 JSON 키가 되므로 바꾸지 말 것.
 *
 * @param to    Expo push token
 * @param data  FE 딥링크용 (계약서 §8) — type / requestId / postId
 */
public record PushMessage(String to, String title, String body, Map<String, Object> data) {
}
