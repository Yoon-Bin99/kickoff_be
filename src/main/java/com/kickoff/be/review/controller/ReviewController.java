package com.kickoff.be.review.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.review.dto.ReviewCreateRequest;
import com.kickoff.be.review.dto.ReviewResponse;
import com.kickoff.be.review.service.ReviewService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 매칭 당사자 팀만. 대상 팀은 서버가 정한다 (계약서 §7). */
    @PostMapping("/api/requests/{requestId}/review")
    public ResponseEntity<ReviewResponse> create(@PathVariable Long requestId,
                                                 @LoginUser User user,
                                                 @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reviewService.create(requestId, user, request));
    }

    /** 팀이 받은 리뷰. 인증 불필요. */
    @GetMapping("/api/teams/{teamId}/reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> getByTeam(
            @PathVariable Long teamId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.getByTeam(teamId, page, size));
    }
}
