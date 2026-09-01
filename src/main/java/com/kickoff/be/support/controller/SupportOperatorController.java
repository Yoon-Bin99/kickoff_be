package com.kickoff.be.support.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.support.dto.SupportChatResponse;
import com.kickoff.be.support.dto.SupportMessageResponse;
import com.kickoff.be.support.dto.SupportRoomResponse;
import com.kickoff.be.support.dto.SupportSendRequest;
import com.kickoff.be.support.service.SupportService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 쪽 문의함 API (계약서 §7-1, v1.22.0).
 *
 * 운영자 판정은 <b>서버만 안다</b> — SUPPORT_OPERATOR_EMAIL 로 지정된 계정이 아니면 403 이고,
 * 미설정이면 아무도 아니다. FE 는 "이 API 가 403 이 아닌지"로 문의함 탭을 띄울지 정한다.
 */
@RestController
@RequiredArgsConstructor
public class SupportOperatorController {

    private final SupportService supportService;

    @GetMapping("/api/support/rooms")
    public ResponseEntity<List<SupportRoomResponse>> rooms(@LoginUser User operator) {
        return ResponseEntity.ok(supportService.rooms(operator));
    }

    @GetMapping("/api/support/rooms/{userId}/chat")
    public ResponseEntity<SupportChatResponse> roomMessages(
            @PathVariable Long userId, @LoginUser User operator,
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(supportService.roomMessages(operator, userId, after, limit));
    }

    @PostMapping("/api/support/rooms/{userId}/chat")
    public ResponseEntity<SupportMessageResponse> reply(
            @PathVariable Long userId, @LoginUser User operator,
            @Valid @RequestBody SupportSendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(supportService.replyAsOperator(operator, userId, request));
    }
}
