package com.kickoff.be.support.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.IntegrationTestSupport;
import com.kickoff.be.support.entity.SupportMessage;
import com.kickoff.be.support.entity.SupportRoom;
import com.kickoff.be.support.entity.SupportSender;
import com.kickoff.be.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * AI 응답기의 <b>두 번째 방어선</b> (계약서 §7-1, v1.22.0).
 *
 * 전송 시점에 운영자 모드가 아니면 AI 호출이 예약된다. 그런데 그 호출은 비동기라,
 * <b>예약과 실행 사이에 사용자가 운영자를 부를 수 있다.</b> 그 창은 짧지만 실재하고,
 * "AI 답변을 기다리다 못해 운영자 연결을 누르는" 것이 가장 흔한 경로다.
 *
 * 그래서 응답기가 실행 직전에 다시 본다. 이 테스트는 그 재확인만 겨냥한다 — 서비스 쪽
 * 분기(SupportChatTest)로는 여기까지 못 온다. 실제로 이 가드를 지워도 통합 테스트가
 * 하나도 안 깨졌다.
 */
class AiSupportResponderTest extends IntegrationTestSupport {

    @Autowired
    private AiSupportResponder responder;

    @Test
    @DisplayName("실행 직전에 운영자 모드가 됐으면 AI 는 답하지 않는다")
    void rechecksOperatorModeBeforeAnswering() {
        User user = createUser("member@example.com", "김감독", "010-1111-1111");
        aiClient.reset();

        // 사용자가 말을 남겼고, AI 호출이 예약된 상태를 만든다
        supportMessageRepository.save(SupportMessage.builder()
                .user(user).sender(SupportSender.USER).content("사람 좀 바꿔 주세요").build());
        // 그런데 실행 전에 운영자를 불렀다
        SupportRoom room = supportRoomRepository.save(SupportRoom.builder().user(user).build());
        room.escalate();
        supportRoomRepository.save(room);

        responder.reply(user.getId());

        // 여기서 답하면 운영자를 부른 사용자에게 기계가 대신 답한다
        assertThat(aiClient.calls()).isEmpty();
        assertThat(supportMessageRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("운영자 모드가 아니면 답한다 — 위 테스트가 늘 통과하는 것이 아님을 보인다")
    void answersWhenNotOperatorMode() {
        User user = createUser("member2@example.com", "이감독", "010-2222-2222");
        aiClient.reset();
        aiClient.willAnswer("도와드리겠습니다.");

        supportMessageRepository.save(SupportMessage.builder()
                .user(user).sender(SupportSender.USER).content("질문 있습니다").build());
        supportRoomRepository.save(SupportRoom.builder().user(user).build());

        responder.reply(user.getId());

        assertThat(aiClient.calls()).hasSize(1);
        assertThat(supportMessageRepository.findAll()).hasSize(2);
    }
}
