package com.kickoff.be.support.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.push.dto.SupportPushEvent;
import com.kickoff.be.support.dto.FaqItem;
import com.kickoff.be.support.dto.SupportChatResponse;
import com.kickoff.be.support.dto.SupportMessageResponse;
import com.kickoff.be.support.dto.SupportRoomResponse;
import com.kickoff.be.support.dto.SupportSendRequest;
import com.kickoff.be.support.entity.SupportMessage;
import com.kickoff.be.support.entity.SupportRoom;
import com.kickoff.be.support.entity.SupportSender;
import com.kickoff.be.support.repository.SupportMessageRepository;
import com.kickoff.be.support.repository.SupportRoomRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객센터 문의 (계약서 §7-1, v1.22.0).
 *
 * 3단 응대의 <b>분기가 전부 여기 모인다</b> — FAQ 는 서버를 안 거치고, AI 는 운영자 모드가
 * 아닐 때만 부르며, 운영자 모드에서는 AI 가 침묵하고 푸시가 나간다.
 */
@Service
@RequiredArgsConstructor
public class SupportService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final long NO_CURSOR = 0L;
    /** 계약서 §7-1 — 사용자당 분당 5회. */
    private static final int RATE_LIMIT_PER_MINUTE = 5;

    private final SupportMessageRepository messageRepository;
    private final SupportRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SupportKnowledge knowledge;
    private final SupportProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    // ── FAQ (계약서 §7-1 1단계)

    /** 서버에 저장하지도, AI 를 부르지도 않는다. 무료·즉답이라는 게 이 단계의 존재 이유다. */
    @Transactional(readOnly = true)
    public List<FaqItem> faq() {
        return knowledge.faq();
    }

    // ── 사용자 쪽

    @Transactional
    public SupportChatResponse myMessages(User user, Long after, Integer limit) {
        // 첫 조회에 방이 생긴다 (계약서 §7-1). 조회가 쓰기를 하는 게 어색하지만, 그러지
        // 않으면 FE 가 "방 만들기"를 따로 불러야 하고 그 호출을 빠뜨리면 전송이 실패한다.
        roomOf(user);
        return new SupportChatResponse(read(user.getId(), after, limit));
    }

    @Transactional
    public SupportMessageResponse send(User user, SupportSendRequest request) {
        SupportRoom room = roomOf(user);
        if (!room.isOperatorMode()) {
            // 한도는 <b>AI 를 부를 때만</b> 본다 (계약서 §7-1 — "AI 호출 분당 5회").
            // 운영자 모드에도 걸면 사람과 이야기하는 사용자가 급할 때 막힌다.
            requireUnderRateLimit(user);
        }

        SupportMessage saved = save(user, SupportSender.USER, request.content());

        if (room.isOperatorMode()) {
            // 운영자 모드에서는 AI 가 침묵하고 운영자에게 알림이 간다 (계약서 §7-1).
            notifyOperator(user, request.content());
        } else {
            // AI 응대 중에는 운영자 푸시를 보내지 않는다 — 계약서 §7-1 표에 명시돼 있다.
            // 여기서 보내면 운영자가 AI 가 처리 중인 문의까지 전부 알림으로 받는다.
            //
            // 호출은 <b>커밋 이후에 비동기로</b> 한다. 여기서 바로 부르면 제공자 왕복
            // (최대 20초) 동안 사용자 트랜잭션과 DB 커넥션이 붙잡히고, 전송 응답도 그만큼
            // 늦는다. 계약서가 "다음 폴링에 잡힘"이라고 적은 것도 그 모양이다.
            eventPublisher.publishEvent(new AiReplyRequestedEvent(user.getId()));
        }
        return SupportMessageResponse.of(saved);
    }

    /**
     * 운영자 연결 (계약서 §7-1 3단계). <b>되돌리기는 v1 에 없다.</b>
     *
     * 이미 운영자 모드여도 204 다 — 두 번 눌렀다고 실패할 이유가 없고, FE 가 버튼을
     * 상시 노출하므로 중복 호출이 정상 경로다.
     */
    @Transactional
    public void escalate(User user) {
        SupportRoom room = roomOf(user);
        boolean wasAlreadyOperatorMode = room.isOperatorMode();
        room.escalate();
        if (!wasAlreadyOperatorMode) {
            // 전환 시점에 한 번 알린다. 두 번째부터 또 보내면 버튼을 누를 때마다
            // 운영자 알림이 쌓인다.
            notifyOperator(user, "운영자 연결을 요청했습니다.");
        }
    }

    // ── 운영자 쪽

    /**
     * 문의함 목록 (계약서 §7-1). 메시지가 있는 방만 나온다 — 빈 방(첫 조회로 만들어진
     * 방)은 집계 쿼리에서 자연히 빠진다.
     */
    @Transactional(readOnly = true)
    public List<SupportRoomResponse> rooms(User operator) {
        requireOperator(operator);

        List<Long> lastIds = messageRepository.findLastIdPerUser().stream()
                .map(row -> (Long) row[1])
                .toList();
        if (lastIds.isEmpty()) {
            return List.of();
        }
        return messageRepository.findAllWithUserByIdIn(lastIds).stream()
                .sorted(Comparator.comparing(SupportMessage::getCreatedAt).reversed())
                .map(m -> new SupportRoomResponse(m.getUser().getId(), m.getUser().getNickname(),
                        SupportMessageResponse.of(m)))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportChatResponse roomMessages(User operator, Long userId, Long after,
                                            Integer limit) {
        requireOperator(operator);
        requireUserExists(userId);
        return new SupportChatResponse(read(userId, after, limit));
    }

    @Transactional
    public SupportMessageResponse replyAsOperator(User operator, Long userId,
                                                  SupportSendRequest request) {
        requireOperator(operator);
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 운영자가 답했다면 그 방은 사람이 보고 있다는 뜻이다. 에스컬레이트를 안 눌렀어도
        // 여기서 운영자 모드로 넘긴다 — 안 그러면 운영자 답변 바로 다음에 AI 가 끼어든다.
        roomOf(owner).escalate();

        SupportMessage saved = save(owner, SupportSender.OPERATOR, request.content());
        eventPublisher.publishEvent(
                SupportPushEvent.toUser(owner.getId(), request.content()));
        return SupportMessageResponse.of(saved);
    }

    /** FE 가 "문의함이 403 인지"로 운영자 여부를 판정한다 (계약서 §7-1 강등 패턴). */
    private void requireOperator(User user) {
        if (!properties.isOperator(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    // ── 공통

    private SupportRoom roomOf(User user) {
        return roomRepository.findByUser_Id(user.getId())
                .orElseGet(() -> roomRepository.save(SupportRoom.builder().user(user).build()));
    }

    private SupportMessage save(User owner, SupportSender sender, String content) {
        return messageRepository.save(SupportMessage.builder()
                .user(owner)
                .sender(sender)
                .content(content)
                .build());
    }

    /**
     * 폴링 (계약서 §7-1, §6-1과 같은 규칙). {@code after} 가 없으면 <b>최신</b> limit 개를
     * 오름차순으로, 있으면 그 이후를 오름차순으로.
     */
    private List<SupportMessageResponse> read(Long userId, Long after, Integer limit) {
        long cursor = after == null ? NO_CURSOR : after;
        Pageable page = PageRequest.ofSize(clampLimit(limit));

        List<SupportMessage> found;
        if (after == null) {
            // 최신부터 받아 뒤집는다. 오름차순으로 자르면 방의 가장 오래된 것부터 나와,
            // 문의가 길어질수록 화면에 옛날 이야기만 뜬다.
            found = new ArrayList<>(
                    messageRepository.findByUser_IdAndIdGreaterThanOrderByIdDesc(userId, cursor, page));
            java.util.Collections.reverse(found);
        } else {
            found = messageRepository.findByUser_IdAndIdGreaterThanOrderByIdAsc(userId, cursor, page);
        }
        return found.stream().map(SupportMessageResponse::of).toList();
    }

    /**
     * AI 호출 한도 (계약서 §7-1 — 사용자당 분당 5회).
     *
     * <b>저장 전에</b> 본다. 저장한 뒤에 막으면 사용자 메시지는 쌓이는데 답이 없는 방이
     * 되고, 한도가 풀려도 그 말들에는 영영 답이 안 달린다.
     */
    private void requireUnderRateLimit(User user) {
        long recent = messageRepository.countUserMessagesSince(
                user.getId(), OffsetDateTime.now().minusMinutes(1));
        if (recent >= RATE_LIMIT_PER_MINUTE) {
            throw new BusinessException(ErrorCode.SUPPORT_RATE_LIMITED);
        }
    }

    private void notifyOperator(User user, String content) {
        operatorUserId().ifPresent(operatorId ->
                eventPublisher.publishEvent(SupportPushEvent.toOperator(
                        operatorId, user.getId(), user.getNickname(), content)));
    }

    /**
     * 운영자 계정의 id. 미설정이거나 그 이메일의 계정이 없으면 비어 있다 —
     * 알림만 안 갈 뿐 문의 저장은 정상이다. 운영자가 없다고 사용자가 글을 못 남기면 안 된다.
     */
    private java.util.Optional<Long> operatorUserId() {
        String email = properties.operatorEmail();
        if (email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        return userRepository.findAllByEmailIgnoreCase(email).stream()
                .findFirst()
                .map(User::getId);
    }

    private void requireUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
