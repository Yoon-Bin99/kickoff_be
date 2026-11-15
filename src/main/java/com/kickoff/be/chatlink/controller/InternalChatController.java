package com.kickoff.be.chatlink.controller;

import com.kickoff.be.chatlink.dto.ChatPushRequest;
import com.kickoff.be.chatlink.dto.RoomContext;
import com.kickoff.be.chatlink.service.InternalChatService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅 서버 전용 (계약서 §10). <b>{@code /api} 프리픽스가 없다</b> — FE 가 부르는 표면과
 * 주소 공간부터 갈라 두려는 것이고, 그래야 공개 경로에서 {@code /internal/**} 을 통째로
 * 막는 인프라 설정도 한 줄로 끝난다.
 *
 * 사용자 JWT 를 타지 않는다. 자격은 {@code X-Internal-Secret} 헤더뿐이고 검사는
 * {@code InternalSecretFilter} 가 한다 — 여기까지 왔다면 이미 통과한 호출이다.
 */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class InternalChatController {

    private final InternalChatService internalChatService;

    @GetMapping("/rooms/{requestId}")
    public RoomContext room(@PathVariable Long requestId) {
        return internalChatService.room(requestId);
    }

    @GetMapping("/users/{userId}/rooms")
    public List<RoomContext> rooms(@PathVariable Long userId) {
        return internalChatService.rooms(userId);
    }

    /**
     * 202 다 (계약서 §10). 발송 결과를 기다리지 않는다 — 채팅 서버는 메시지를 이미
     * 저장했고, 푸시가 나갔는지로 그 결과가 달라지지 않는다.
     */
    @PostMapping("/push")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void push(@Valid @RequestBody ChatPushRequest request) {
        internalChatService.push(request);
    }
}
