package com.kickoff.be.matchrequest.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.matchrequest.dto.RequestCreateRequest;
import com.kickoff.be.matchrequest.dto.RequestResponse;
import com.kickoff.be.matchrequest.service.MatchRequestService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MatchRequestController {

    private final MatchRequestService requestService;

    @PostMapping("/api/posts/{postId}/requests")
    public ResponseEntity<RequestResponse> create(
            @PathVariable Long postId,
            @LoginUser User user,
            @Valid @RequestBody(required = false) RequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(requestService.create(postId, user, request));
    }

    /** 글 작성자만. */
    @GetMapping("/api/posts/{postId}/requests")
    public ResponseEntity<List<RequestResponse>> getByPost(@PathVariable Long postId,
                                                           @LoginUser User user) {
        return ResponseEntity.ok(requestService.getByPost(postId, user));
    }

    /** 내 팀 글에 온 신청 전체. FE 매칭관리 탭이 글마다 조회하지 않아도 되게. */
    @GetMapping("/api/requests/received")
    public ResponseEntity<List<RequestResponse>> getReceived(@LoginUser User user) {
        return ResponseEntity.ok(requestService.getReceived(user));
    }

    @GetMapping("/api/requests/sent")
    public ResponseEntity<List<RequestResponse>> getSent(@LoginUser User user) {
        return ResponseEntity.ok(requestService.getSent(user));
    }

    @PostMapping("/api/requests/{requestId}/accept")
    public ResponseEntity<RequestResponse> accept(@PathVariable Long requestId,
                                                  @LoginUser User user) {
        return ResponseEntity.ok(requestService.accept(requestId, user));
    }

    @PostMapping("/api/requests/{requestId}/reject")
    public ResponseEntity<RequestResponse> reject(@PathVariable Long requestId,
                                                  @LoginUser User user) {
        return ResponseEntity.ok(requestService.reject(requestId, user));
    }

    @PostMapping("/api/requests/{requestId}/confirm-deposit")
    public ResponseEntity<RequestResponse> confirmDeposit(@PathVariable Long requestId,
                                                          @LoginUser User user) {
        return ResponseEntity.ok(requestService.confirmDeposit(requestId, user));
    }

    /** 신청한 팀만. PENDING 신청을 CANCELED 로. */
    @DeleteMapping("/api/requests/{requestId}")
    public ResponseEntity<Void> cancel(@PathVariable Long requestId, @LoginUser User user) {
        requestService.cancel(requestId, user);
        return ResponseEntity.noContent().build();
    }
}
