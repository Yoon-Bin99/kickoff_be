package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.team.dto.TeamMemberCreateRequest;
import com.kickoff.be.team.dto.TeamMemberResponse;
import com.kickoff.be.team.dto.TeamMemberUpdateRequest;
import com.kickoff.be.team.service.TeamMemberService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 팀원 명단 (계약서 §4-1, v1.8.0). 조회만 인증 불필요. */
@RestController
@RequestMapping("/api/teams/{teamId}/members")
@RequiredArgsConstructor
public class TeamMemberController {

    private final TeamMemberService teamMemberService;

    @GetMapping
    public ResponseEntity<List<TeamMemberResponse>> list(@PathVariable Long teamId) {
        return ResponseEntity.ok(teamMemberService.list(teamId));
    }

    @PostMapping
    public ResponseEntity<TeamMemberResponse> add(
            @PathVariable Long teamId, @LoginUser User user,
            @Valid @RequestBody TeamMemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamMemberService.add(teamId, user, request));
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<TeamMemberResponse> update(
            @PathVariable Long teamId, @PathVariable Long memberId, @LoginUser User user,
            @Valid @RequestBody TeamMemberUpdateRequest request) {
        return ResponseEntity.ok(teamMemberService.update(teamId, memberId, user, request));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long memberId,
                                       @LoginUser User user) {
        teamMemberService.delete(teamId, memberId, user);
        return ResponseEntity.noContent().build();
    }
}
