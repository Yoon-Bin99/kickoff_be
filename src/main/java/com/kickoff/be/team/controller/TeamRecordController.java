package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.common.PageResponse;
import com.kickoff.be.team.dto.TeamRecordCreateRequest;
import com.kickoff.be.team.dto.TeamRecordResponse;
import com.kickoff.be.team.service.TeamRecordService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 경기 기록 (계약서 §4-1, v1.8.0). 조회만 인증 불필요. 수정은 없다. */
@RestController
@RequestMapping("/api/teams/{teamId}/records")
@RequiredArgsConstructor
public class TeamRecordController {

    private final TeamRecordService teamRecordService;

    @GetMapping
    public ResponseEntity<PageResponse<TeamRecordResponse>> list(
            @PathVariable Long teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(teamRecordService.list(teamId, page, size));
    }

    @PostMapping
    public ResponseEntity<TeamRecordResponse> add(
            @PathVariable Long teamId, @LoginUser User user,
            @Valid @RequestBody TeamRecordCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamRecordService.add(teamId, user, request));
    }

    @DeleteMapping("/{recordId}")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long recordId,
                                       @LoginUser User user) {
        teamRecordService.delete(teamId, recordId, user);
        return ResponseEntity.noContent().build();
    }
}
