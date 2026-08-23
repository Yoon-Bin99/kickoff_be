package com.kickoff.be.team;

import com.kickoff.be.auth.LoginUser;
import com.kickoff.be.team.dto.TeamCreateRequest;
import com.kickoff.be.team.dto.TeamResponse;
import com.kickoff.be.team.dto.TeamUpdateRequest;
import com.kickoff.be.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> create(@LoginUser User user,
                                               @Valid @RequestBody TeamCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(teamService.create(user, request));
    }

    @GetMapping("/me")
    public ResponseEntity<TeamResponse> getMine(@LoginUser User user) {
        return ResponseEntity.ok(teamService.getMine(user));
    }

    /** 인증 불필요. 토큰이 있으면 isMine 을 채운다. */
    @GetMapping("/{teamId}")
    public ResponseEntity<TeamResponse> get(@PathVariable Long teamId, @LoginUser User viewer) {
        return ResponseEntity.ok(teamService.get(teamId, viewer));
    }

    @PatchMapping("/{teamId}")
    public ResponseEntity<TeamResponse> update(@PathVariable Long teamId, @LoginUser User user,
                                               @Valid @RequestBody TeamUpdateRequest request) {
        return ResponseEntity.ok(teamService.update(teamId, user, request));
    }
}
