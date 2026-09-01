package com.kickoff.be.support.service;

import com.kickoff.be.support.client.AiSupportClient;
import com.kickoff.be.support.client.AiSupportProperties;
import com.kickoff.be.support.entity.SupportMessage;
import com.kickoff.be.support.entity.SupportSender;
import com.kickoff.be.support.repository.SupportMessageRepository;
import com.kickoff.be.support.repository.SupportRoomRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI 상담사 (계약서 §7-1 2단계, v1.22.0).
 *
 * 사용자 메시지가 <b>커밋된 뒤</b> 비동기로 돈다. 전송 트랜잭션 안에서 제공자를 부르면
 * 왕복 동안 DB 커넥션이 붙잡히고, 롤백되면 없는 메시지에 답이 달린다.
 *
 * <b>여기서 나는 어떤 실패도 사용자 전송을 실패시키지 않는다.</b> 이미 커밋된 뒤라
 * 그럴 수도 없지만, 그게 이 설계의 목적이기도 하다 — AI 가 못 답한다고 사용자의 문의가
 * 사라지면 안 된다. 그 말은 운영자가 읽어야 한다.
 */
@Slf4j
@Component
public class AiSupportResponder {

    /** 계약서 §7-1 — 응답 실패 시 강등 안내. */
    private static final String FALLBACK =
            "지금은 답변을 드리기 어렵습니다. 운영자에게 전달해 드릴까요? "
                    + "아래 \"운영자 연결하기\"를 눌러 주세요.";
    /** AI 에게 넘길 최근 대화 길이. 길수록 비싸고, 고객센터 문의는 대개 짧다. */
    private static final int HISTORY_LIMIT = 20;

    private final SupportMessageRepository messageRepository;
    private final SupportRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SupportKnowledge knowledge;
    private final AiSupportProperties properties;
    /**
     * 키가 없으면 어댑터 빈 자체가 없다. {@code ObjectProvider} 로 받아 <b>없어도 기동은
     * 되게</b> 한다 — AI 를 안 쓰는 배포에서 앱 전체가 못 뜨면 안 된다.
     */
    private final ObjectProvider<AiSupportClient> clientProvider;

    public AiSupportResponder(SupportMessageRepository messageRepository,
                              SupportRoomRepository roomRepository,
                              UserRepository userRepository,
                              SupportKnowledge knowledge,
                              AiSupportProperties properties,
                              ObjectProvider<AiSupportClient> clientProvider) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.knowledge = knowledge;
        this.properties = properties;
        this.clientProvider = clientProvider;
    }

    /**
     * <b>{@code REQUIRES_NEW} 가 반드시 필요하다.</b> AFTER_COMMIT 리스너는 방금 커밋된
     * 트랜잭션의 뒷정리 구간에서 도는데, 거기서 그냥 저장하면 <b>이미 끝난 트랜잭션에
     * 얹혀 조용히 사라진다</b> — 예외도 로그도 없고 행만 안 생긴다. 실제로 그렇게 짰다가
     * "AI 가 답을 안 한다"로 드러났고, 원인이 AI 쪽에 있어 보이지 트랜잭션에 있어
     * 보이지 않았다.
     *
     * {@code @Async} 로 다른 스레드에 나가면 이 문제가 저절로 사라지지만, 그건 실행기
     * 설정에 기대는 것이다. 여기서 못박아 두면 동기로 돌아도 안전하다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AiReplyRequestedEvent event) {
        try {
            reply(event.userId());
        } catch (RuntimeException e) {
            // 여기까지 새어 나오면 남는 건 로그뿐이다. 사용자 방에는 답이 안 달리고,
            // 사용자는 "운영자 연결하기"로 사람을 부를 수 있다.
            log.warn("AI 상담 처리 실패 — userId={}", event.userId(), e);
        }
    }

    void reply(Long userId) {
        AiSupportClient client = clientProvider.getIfAvailable();
        if (!properties.isUsable() || client == null) {
            // 스위치가 꺼졌거나 키가 없다. 이건 실패가 아니라 "AI 를 안 쓰는 배포"라
            // 강등 안내조차 남기지 않는다 — 남기면 방마다 안내가 쌓인다.
            log.debug("AI 미사용 — 답변 생략 (userId={})", userId);
            return;
        }
        Optional<User> owner = userRepository.findById(userId);
        if (owner.isEmpty()) {
            return;
        }
        // 기다리는 동안 사용자가 운영자를 불렀을 수 있다. 그때는 답하지 않는다 —
        // 운영자 모드에서 AI 가 끼어들면 사용자는 사람과 이야기하는 줄 알고 있다.
        boolean operatorMode = roomRepository.findByUser_Id(userId)
                .map(room -> room.isOperatorMode())
                .orElse(false);
        if (operatorMode) {
            log.debug("운영자 모드 — AI 답변 생략 (userId={})", userId);
            return;
        }

        // 최신 HISTORY_LIMIT 개를 받아 뒤집는다 — 오래된 것부터 넘겨야 대화 순서가 맞다.
        // (List.reversed() 는 Java 21 이라 여기서는 못 쓴다. 이 프로젝트는 17 이다.)
        List<SupportMessage> history = new ArrayList<>(messageRepository
                .findByUser_IdAndIdGreaterThanOrderByIdDesc(userId, 0L,
                        PageRequest.ofSize(HISTORY_LIMIT)));
        Collections.reverse(history);
        List<AiSupportClient.AiTurn> turns = history.stream()
                .map(m -> new AiSupportClient.AiTurn(m.isFrom(SupportSender.USER), m.getContent()))
                .toList();
        if (turns.isEmpty()) {
            return;
        }

        String answer;
        try {
            answer = client.reply(systemPrompt(), turns);
        } catch (RuntimeException e) {
            // 타임아웃·인증 실패·크레딧 부족이 전부 여기로 온다. 사용자에게는 같은
            // 안내가 나가고 원인은 로그가 말한다 (계약서 §7-1 강등).
            log.warn("AI 응답 실패 — 강등 안내로 대체 (userId={}): {}", userId, e.getMessage());
            answer = FALLBACK;
        }
        messageRepository.save(SupportMessage.builder()
                .user(owner.get())
                .sender(SupportSender.AI)
                .content(clamp(answer))
                .build());
    }

    /**
     * 역할·하드 제약·서비스 지식을 합친다. 지식 본문은 supervisor 가 관리하는 문서에서
     * 그대로 온다 — 여기서 요약하거나 고쳐 쓰지 않는다. 그러면 문서를 고쳐도 프롬프트가
     * 안 바뀌는 자리가 생긴다.
     */
    private String systemPrompt() {
        return """
                너는 조기축구 팀 매칭 앱 "킥오프"의 고객센터 AI 상담사다.
                아래 지식만을 근거로, 존댓말로 짧고 친절하게 답한다.
                지식에 없는 것은 지어내지 말고 운영자 연결을 권한다.

                %s
                """.formatted(knowledge.aiKnowledge());
    }

    /**
     * 메시지 컬럼이 500자다. 모델이 그보다 길게 답하면 <b>저장이 실패</b>하는데, 그러면
     * 답이 통째로 사라지고 사용자는 아무 반응도 못 본다. 잘라서라도 남기는 쪽이 낫다.
     */
    private String clamp(String answer) {
        return answer.length() <= 500 ? answer : answer.substring(0, 497) + "...";
    }
}
