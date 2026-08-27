package com.kickoff.be.chat.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.chat.dto.ChatMessageResponse;
import com.kickoff.be.chat.dto.ChatResponse;
import com.kickoff.be.chat.dto.ChatSendRequest;
import com.kickoff.be.chat.service.ChatService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭 채팅 (계약서 §6-1, v1.12.0). 둘 다 인증 필요, 당사자 팀 주장만.
 *
 * 경로가 {@code /api/requests/{requestId}/chat} 인 건 방이 곧 매칭이기 때문이다 —
 * 별도의 방 id 가 없다.
 */
@RestController
@RequestMapping("/api/requests/{requestId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** after 가 없으면 최신 limit 개(첫 로드), 있으면 그 이후(폴링). 응답은 항상 오름차순. */
    @GetMapping
    public ResponseEntity<ChatResponse> messages(
            @PathVariable Long requestId, @LoginUser User user,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(chatService.messages(requestId, user, after, limit));
    }

    @PostMapping
    public ResponseEntity<ChatMessageResponse> send(
            @PathVariable Long requestId, @LoginUser User user,
            @Valid @RequestBody ChatSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.send(requestId, user, request));
    }
}
