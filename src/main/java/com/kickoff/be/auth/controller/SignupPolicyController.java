package com.kickoff.be.auth.controller;

import com.kickoff.be.auth.dto.SignupPolicyResponse;
import com.kickoff.be.verification.service.VerificationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 정책 알림 (계약서 §3-2, v1.16.1). 인증 불필요 — 가입 화면을 그리기 전에 부른다.
 *
 * <b>왜 값을 물어보게 했는가.</b> v1.16.0 까지 FE 는 "발송 엔드포인트가 있는가"로 인증
 * UI 를 켤지 정했다. 그런데 엔드포인트는 스위치와 무관하게 항상 동작하므로, 서버가 아직
 * 강제하지 않는 기간에도 화면은 인증을 요구하게 된다. SMS 제공자가 붙기 전에는 아무도
 * 인증번호를 받을 수 없으니 그동안 새 앱의 가입이 통째로 막힌다 — FE 가 찾아낸 조합이다.
 *
 * 존재가 아니라 <b>값</b>을 보게 하면 그 창이 사라진다. 서버가 강제하지 않는 동안에는
 * 화면도 강제하지 않고, 스위치를 켜는 순간 화면이 따라온다. 최종 진실은 어차피 signup 의
 * 400 PHONE_NOT_VERIFIED 이므로 이 값이 조금 늦게 반영돼도 안전 방향으로만 틀린다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SignupPolicyController {

    private final VerificationProperties verificationProperties;

    @GetMapping("/signup-policy")
    public ResponseEntity<SignupPolicyResponse> signupPolicy() {
        return ResponseEntity.ok(
                new SignupPolicyResponse(verificationProperties.phoneRequired()));
    }
}
