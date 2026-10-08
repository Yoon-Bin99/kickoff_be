package com.kickoff.be.team.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.team.dto.SquadResponse;
import com.kickoff.be.team.dto.SquadSaveRequest;
import com.kickoff.be.team.dto.SquadSummary;
import com.kickoff.be.team.service.SquadService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 스쿼드 메이커 (계약서 §4-4, v1.28.0).
 *
 * <b>읽기도 권한이 필요하다</b> — 소속 전원(OWNER·ADMIN·MEMBER)만 본다. 비로그인은 401,
 * 로그인했지만 비소속이면 403 이다(계약서 §4-4, v1.28.1). 팀 페이지의 다른 조회들
 * (§4-1 명단·전적)과 다른 점이라 눈에 띄게 적어 둔다 — 그쪽은 공개다.
 *
 * 공유는 서버가 관여하지 않는다. FE 가 경기장 그림을 이미지로 만들어 OS 공유창으로 보낸다.
 */
@RestController
@RequestMapping("/api/teams/{teamId}/squads")
@RequiredArgsConstructor
public class SquadController {

    private final SquadService squadService;

    /** 목록 — updatedAt 내림차순, 페이지 없음(최대 30). */
    @GetMapping
    public ResponseEntity<List<SquadSummary>> list(@PathVariable Long teamId,
                                                   @LoginUser User user) {
        return ResponseEntity.ok(squadService.list(teamId, user));
    }

    @PostMapping
    public ResponseEntity<SquadResponse> create(@PathVariable Long teamId, @LoginUser User user,
                                                @Valid @RequestBody SquadSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(squadService.create(teamId, user, request));
    }

    @GetMapping("/{squadId}")
    public ResponseEntity<SquadResponse> get(@PathVariable Long teamId,
                                             @PathVariable Long squadId,
                                             @LoginUser User user) {
        return ResponseEntity.ok(squadService.get(teamId, squadId, user));
    }

    /** 전체 교체 (계약서 §4-4). 부분 수정은 없다 — 보드 하나를 통째로 저장하는 UI 다. */
    @PutMapping("/{squadId}")
    public ResponseEntity<SquadResponse> update(@PathVariable Long teamId,
                                                @PathVariable Long squadId,
                                                @LoginUser User user,
                                                @Valid @RequestBody SquadSaveRequest request) {
        return ResponseEntity.ok(squadService.update(teamId, squadId, user, request));
    }

    @DeleteMapping("/{squadId}")
    public ResponseEntity<Void> delete(@PathVariable Long teamId, @PathVariable Long squadId,
                                       @LoginUser User user) {
        squadService.delete(teamId, squadId, user);
        return ResponseEntity.noContent().build();
    }
}
