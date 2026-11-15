package com.kickoff.be.chatlink.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.chatlink.dto.ChatOutboxMessage;
import com.kickoff.be.chatlink.entity.ChatOutbox;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 채팅 서버로 나가는 본문의 모양 (계약서 §10 의 두 예시).
 *
 * <b>칸 하나가 더 붙거나 빠지면 받는 쪽이 깨진다.</b> 계약서의 ROOM_INVALIDATED 예시에는
 * {@code teamIds} 가 아예 없다 — 빈 배열을 덧붙이면 "팀 기록도 지우라는 뜻인가"로 읽힐
 * 여지가 생기고, 모르는 칸에 엄격한 파서라면 그대로 거절한다. 전역 Jackson 설정이 null
 * 필드도 그대로 내보내기 때문에 레코드가 아니라 맵으로 만든다.
 */
class ChatEventBodyTest {

    private static final ChatLinkProperties PROPERTIES =
            new ChatLinkProperties("test-secret", "http://localhost:18090");

    @Test
    @DisplayName("ROOM_INVALIDATED 본문에는 teamIds 가 없다")
    void roomInvalidatedBodyHasNoTeamIds() {
        Map<String, Object> body = bodyOf(ChatOutbox.roomInvalidated(List.of(7L), now()));

        assertThat(body).containsOnlyKeys("eventId", "type", "requestIds");
        assertThat(body).containsEntry("type", "ROOM_INVALIDATED");
        assertThat(body).containsEntry("requestIds", List.of(7L));
    }

    @Test
    @DisplayName("PURGE 본문에는 requestIds 와 teamIds 가 함께 있다")
    void purgeBodyHasBothLists() {
        Map<String, Object> body = bodyOf(ChatOutbox.purge(List.of(7L, 9L, 15L), List.of(3L),
                now()));

        assertThat(body).containsOnlyKeys("eventId", "type", "requestIds", "teamIds");
        assertThat(body).containsEntry("type", "PURGE");
        assertThat(body).containsEntry("requestIds", List.of(7L, 9L, 15L));
        assertThat(body).containsEntry("teamIds", List.of(3L));
    }

    private Map<String, Object> bodyOf(ChatOutbox event) {
        return new ChatServerClient(PROPERTIES).body(ChatOutboxMessage.of(event));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
