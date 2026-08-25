package com.kickoff.be.auth.controller;

import com.kickoff.be.auth.dto.AuthResponse;
import com.kickoff.be.auth.dto.LoginRequest;
import com.kickoff.be.auth.dto.RefreshRequest;
import com.kickoff.be.auth.dto.SignupRequest;
import com.kickoff.be.auth.dto.TokenResponse;
import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.auth.service.AuthService;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** 새 토큰 쌍 (계약서 §3, v1.7.0). 인증 불필요 — refresh token 자체가 자격 증명이다. */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    /** 서버의 refresh token 폐기 (계약서 §3, v1.7.0). 멱등이라 항상 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginUser User user) {
        authService.logout(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@LoginUser User user) {
        return ResponseEntity.ok(authService.me(user));
    }
}
