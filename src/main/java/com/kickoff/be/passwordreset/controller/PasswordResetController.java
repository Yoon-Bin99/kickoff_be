package com.kickoff.be.passwordreset.controller;

import com.kickoff.be.passwordreset.dto.PasswordResetConfirmRequest;
import com.kickoff.be.passwordreset.dto.PasswordResetRequest;
import com.kickoff.be.passwordreset.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비밀번호 재설정 (계약서 §3-3, v1.23.0). 인증 불필요 — 로그인을 못 하는 사람이 쓰는 API 다.
 */
@RestController
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /** 계정이 없어도, 소셜이어도, 발송이 실패해도 204 다 (계약서 §3-3). */
    @PostMapping("/api/auth/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.request(request);
    }

    @PostMapping("/api/auth/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
    }
}
