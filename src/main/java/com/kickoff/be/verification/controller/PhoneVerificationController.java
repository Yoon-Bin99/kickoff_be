package com.kickoff.be.verification.controller;

import com.kickoff.be.verification.dto.PhoneVerificationConfirmRequest;
import com.kickoff.be.verification.dto.PhoneVerificationRequest;
import com.kickoff.be.verification.dto.VerificationTokenResponse;
import com.kickoff.be.verification.service.PhoneVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전화번호 문자 인증 (계약서 §3-2, v1.15.0). 둘 다 인증 불필요 — 가입 전에 부르는 API 다.
 *
 * <b>인증 불필요가 기능 요건이기도 하다.</b> FE 는 이 경로를 헤더 없이 호출해서 401 이
 * 오면 "§3-2 를 모르는 옛 서버"로 판정한다 (계약서 §3-2 구버전 클라이언트 감지).
 * 신버전이 여기서 401 을 내면 그 판정이 뒤집혀, FE 가 멀쩡한 서버를 옛 서버로 보고
 * 인증 UI 를 감춘다. SecurityConfig 의 permitAll 이 빠지면 그 상태가 된다.
 */
@RestController
@RequestMapping("/api/auth/phone/verifications")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService verificationService;

    /** 204 다 — 코드는 절대 응답에 싣지 않는다 (계약서 §3-2). */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@Valid @RequestBody PhoneVerificationRequest request) {
        verificationService.send(request.phone());
    }

    @PostMapping("/confirm")
    public ResponseEntity<VerificationTokenResponse> confirm(
            @Valid @RequestBody PhoneVerificationConfirmRequest request) {
        return ResponseEntity.ok(new VerificationTokenResponse(
                verificationService.confirm(request.phone(), request.code())));
    }
}
