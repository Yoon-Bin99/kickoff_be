package com.kickoff.be.block.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.block.dto.BlockCreateRequest;
import com.kickoff.be.block.dto.BlockResponse;
import com.kickoff.be.block.service.BlockService;
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

/** 차단 (계약서 §7-2, v1.35.0). */
@RestController
@RequestMapping("/api/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    /** 이미 차단한 팀이면 200 으로 같은 응답을 준다 (멱등 — 계약서 §7-2). */
    @PostMapping
    public ResponseEntity<BlockResponse> block(@LoginUser User user,
                                               @Valid @RequestBody BlockCreateRequest request) {
        BlockService.Created created = blockService.block(user, request);
        return ResponseEntity.status(created.fresh() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(created.response());
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> unblock(@LoginUser User user, @PathVariable Long teamId) {
        blockService.unblock(user, teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<BlockResponse>> list(@LoginUser User user) {
        return ResponseEntity.ok(blockService.list(user));
    }
}
