package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.team.dto.TeamAdminCreateRequest;
import com.kickoff.be.team.dto.TeamAdminResponse;
import com.kickoff.be.team.service.TeamAdminService;
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

/** 팀 관리자 (계약서 §4-2, v1.9.0). 전부 인증 필요. */
@RestController
@RequestMapping("/api/teams/{teamId}/admins")
@RequiredArgsConstructor
public class TeamAdminController {

    private final TeamAdminService teamAdminService;

    /** 소유자·관리자만 */
    @GetMapping
    public ResponseEntity<List<TeamAdminResponse>> list(@PathVariable Long teamId,
                                                        @LoginUser User user) {
        return ResponseEntity.ok(teamAdminService.list(teamId, user));
    }

    /** 소유자만 */
    @PostMapping
    public ResponseEntity<TeamAdminResponse> grant(
            @PathVariable Long teamId, @LoginUser User user,
            @Valid @RequestBody TeamAdminCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamAdminService.grant(teamId, user, request));
    }

    /** 소유자만 */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> revoke(@PathVariable Long teamId, @PathVariable Long userId,
                                       @LoginUser User user) {
        teamAdminService.revoke(teamId, userId, user);
        return ResponseEntity.noContent().build();
    }
}
