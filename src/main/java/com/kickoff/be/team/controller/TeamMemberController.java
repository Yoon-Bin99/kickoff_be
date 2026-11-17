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

/**
 * 팀원 명단 (계약서 §4-1, v1.8.0).
 *
 * <b>조회도 인증이 필요하다</b> (v1.38.0). 명단은 계정이 아니라 감독이 적은 다른 사람의
 * 이름이고, 그 사람들은 가입한 적도 공개에 동의한 적도 없다 — 로그인하지 않은 방문자와
 * 검색 수집기에 드러낼 값이 아니다. 팀 정보·모집글·리뷰는 그대로 공개다.
 *
 * 토큰이 없으면 시큐리티가 401 로 끊는다. 로그인했으면 소속과 무관하게 200 이다 —
 * 상대 팀을 보고 신청할지 정하는 데 쓰는 화면이라 소속까지 요구하지 않는다.
 */
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
