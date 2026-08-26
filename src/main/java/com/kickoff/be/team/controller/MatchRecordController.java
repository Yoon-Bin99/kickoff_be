package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.team.dto.MatchRecordCreateRequest;
import com.kickoff.be.team.dto.TeamRecordResponse;
import com.kickoff.be.team.service.TeamRecordService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매칭에서 기록 만들기 (계약서 §4-1, v1.10.0).
 *
 * 경로가 {@code /api/teams} 가 아니라 {@code /api/requests} 아래인 건 이 행위의 주어가 팀이
 * 아니라 <b>매칭</b>이기 때문이다 — 리뷰({@code POST /api/requests/{requestId}/review})와 같은
 * 자리다. 어느 팀의 기록이 되는지는 클라이언트가 아니라 서버가 매칭에서 정한다.
 *
 * 그래서 TeamRecordController 에 붙이지 않았다. 그쪽은 클래스 레벨 경로가
 * {@code /api/teams/{teamId}/records} 라 메서드 경로가 뒤에 이어 붙는다.
 */
@RestController
@RequiredArgsConstructor
public class MatchRecordController {

    private final TeamRecordService teamRecordService;

    @PostMapping("/api/requests/{requestId}/record")
    public ResponseEntity<TeamRecordResponse> addFromMatch(
            @PathVariable Long requestId, @LoginUser User user,
            @Valid @RequestBody MatchRecordCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamRecordService.addFromMatch(requestId, user, request));
    }
}
