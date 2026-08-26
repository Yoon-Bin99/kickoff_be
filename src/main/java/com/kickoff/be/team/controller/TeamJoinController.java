package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.team.dto.TeamJoinCreateRequest;
import com.kickoff.be.team.dto.TeamJoinRequestItem;
import com.kickoff.be.team.dto.TeamJoinResponse;
import com.kickoff.be.team.service.TeamJoinService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팀 가입·소속 (계약서 §4-3, v1.11.0). 전부 인증 필요. */
@RestController
@RequestMapping("/api/teams/{teamId}")
@RequiredArgsConstructor
public class TeamJoinController {

    private final TeamJoinService teamJoinService;

    /** 가입 신청 — 누구나 (이미 소속이면 409). */
    @PostMapping("/join")
    public ResponseEntity<TeamJoinResponse> apply(
            @PathVariable Long teamId, @LoginUser User user,
            @Valid @RequestBody(required = false) TeamJoinCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamJoinService.apply(teamId, user, request));
    }

    /** 내 대기 신청 취소 — 신청자 본인. */
    @DeleteMapping("/join")
    public ResponseEntity<Void> cancel(@PathVariable Long teamId, @LoginUser User user) {
        teamJoinService.cancel(teamId, user);
        return ResponseEntity.noContent().build();
    }

    /** 대기 목록 — 소유자·관리자만. */
    @GetMapping("/join-requests")
    public ResponseEntity<List<TeamJoinRequestItem>> pending(@PathVariable Long teamId,
                                                             @LoginUser User user) {
        return ResponseEntity.ok(teamJoinService.pendingRequests(teamId, user));
    }

    /** 수락 — 소유자·관리자만. 신청자가 MEMBER 가 되고 명단에 등재된다. */
    @PostMapping("/join-requests/{joinId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long teamId, @PathVariable Long joinId,
                                       @LoginUser User user) {
        teamJoinService.accept(teamId, joinId, user);
        return ResponseEntity.ok().build();
    }

    /** 거절 — 소유자·관리자만. */
    @PostMapping("/join-requests/{joinId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long teamId, @PathVariable Long joinId,
                                       @LoginUser User user) {
        teamJoinService.reject(teamId, joinId, user);
        return ResponseEntity.ok().build();
    }

    /** 탈퇴 — MEMBER 본인. 명단 항목도 함께 지워진다. */
    @DeleteMapping("/membership")
    public ResponseEntity<Void> leave(@PathVariable Long teamId, @LoginUser User user) {
        teamJoinService.leave(teamId, user);
        return ResponseEntity.noContent().build();
    }
}
