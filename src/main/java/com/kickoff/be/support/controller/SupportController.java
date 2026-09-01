package com.kickoff.be.support.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.support.dto.FaqItem;
import com.kickoff.be.support.dto.SupportChatResponse;
import com.kickoff.be.support.dto.SupportMessageResponse;
import com.kickoff.be.support.dto.SupportSendRequest;
import com.kickoff.be.support.service.SupportService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 쪽 고객센터 API (계약서 §7-1, v1.22.0). 전부 인증 필요. */
@RestController
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    /** FAQ 퀵버튼. 서버에 저장하지도 AI 를 부르지도 않는다 — 무료·즉답이다. */
    @GetMapping("/api/support/faq")
    public ResponseEntity<List<FaqItem>> faq() {
        return ResponseEntity.ok(supportService.faq());
    }

    /** after 가 없으면 최신 limit 개(첫 로드), 있으면 그 이후(폴링). 응답은 항상 오름차순. */
    @GetMapping("/api/support/chat")
    public ResponseEntity<SupportChatResponse> messages(
            @LoginUser User user,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(supportService.myMessages(user, after, limit));
    }

    @PostMapping("/api/support/chat")
    public ResponseEntity<SupportMessageResponse> send(
            @LoginUser User user, @Valid @RequestBody SupportSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.send(user, request));
    }

    /**
     * 운영자 연결 (계약서 §7-1). 이후 AI 는 답하지 않고, v1 에서는 되돌리지 않는다.
     * 이미 연결된 방에 또 불러도 204 — FE 가 버튼을 상시 노출하므로 중복 호출이 정상이다.
     */
    @PostMapping("/api/support/chat/escalate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void escalate(@LoginUser User user) {
        supportService.escalate(user);
    }
}
