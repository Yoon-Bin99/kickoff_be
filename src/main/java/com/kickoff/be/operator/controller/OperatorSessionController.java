package com.kickoff.be.operator.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.operator.dto.OperatorSessionRequest;
import com.kickoff.be.operator.dto.OperatorSessionResponse;
import com.kickoff.be.operator.service.OperatorGuard;
import com.kickoff.be.operator.service.OperatorSessionService;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영자 세션 열기 (계약서 §7-2 「운영자 인증」, v1.35.1).
 *
 * <b>이 API 자신은 운영자 세션을 요구하지 않는다</b> — 요구하면 아무도 첫 세션을 열 수
 * 없다. 운영자 계정인지만 보고(403), 그 뒤는 OTP 가 판정한다.
 */
@RestController
@RequiredArgsConstructor
public class OperatorSessionController {

    private final OperatorSessionService sessionService;
    private final OperatorGuard operatorGuard;

    @PostMapping("/api/admin/session")
    public ResponseEntity<OperatorSessionResponse> open(
            @LoginUser User user, @Valid @RequestBody OperatorSessionRequest request) {
        operatorGuard.requireOperatorAccount(user);
        try {
            OperatorSessionResponse response = sessionService.open(user, request.otp());
            operatorGuard.record(user, "운영자 세션 발급", null, null);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            // 실패한 시도도 접속기록에 남긴다 (계약서 §7-2) — 누가 몇 번 틀렸는지는
            // 접근 시도의 기록이고, 그걸 남기지 않으면 잠금이 왜 걸렸는지 알 수 없다.
            operatorGuard.record(user, "운영자 세션 실패", null, null);
            throw e;
        }
    }
}
