package com.kickoff.be.auth.controller;

import com.kickoff.be.auth.cookie.WebTokenDelivery;
import com.kickoff.be.auth.dto.AuthResponse;
import com.kickoff.be.auth.dto.LoginRequest;
import com.kickoff.be.auth.dto.RefreshRequest;
import com.kickoff.be.auth.dto.SignupRequest;
import com.kickoff.be.auth.dto.TokenResponse;
import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.auth.service.AuthService;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.ErrorResponse;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 (계약서 §3).
 *
 * <b>웹과 앱이 갈리는 유일한 자리다</b>(v1.32.0). 웹은 refresh token 을 본문이 아니라
 * HttpOnly 쿠키로 주고받는다 — 갈림은 {@link WebTokenDelivery} 한 곳에 모아 두었고,
 * 헤더가 없으면 지금까지와 한 글자도 다르지 않게 동작한다.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WebTokenDelivery webTokenDelivery;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request,
                                               HttpServletRequest http) {
        return withTokenDelivery(authService.signup(request), http, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest http) {
        return withTokenDelivery(authService.login(request), http, HttpStatus.OK);
    }

    /**
     * 새 토큰 쌍 (계약서 §3, v1.7.0). 인증 불필요 — refresh token 자체가 자격 증명이다.
     *
     * <b>웹은 본문을 읽지 않는다</b>(v1.32.0). 쿠키의 토큰으로 검사·로테이션하고 새 쿠키를
     * 준다. 본문에 토큰을 실어 보내도 무시하는 것이 중요하다 — 둘 중 하나를 고르게 두면
     * 공격자가 쿠키 대신 본문을 써서 Origin 검사를 우회할 길이 생긴다.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest http, HttpServletResponse httpResponse) {
        if (!webTokenDelivery.isWeb(http)) {
            return ResponseEntity.ok(authService.refresh(requireBodyToken(request)));
        }

        webTokenDelivery.requireTrustedOrigin(http);
        String token = webTokenDelivery.readCookie(http);
        if (token == null) {
            throw invalidRefreshToken(httpResponse);
        }
        TokenResponse response;
        try {
            response = authService.refresh(token);
        } catch (BusinessException e) {
            // 쓸 수 없는 쿠키는 지워 준다. 남겨 두면 브라우저가 매 시도마다 같은 죽은
            // 토큰을 다시 보내고, 사용자는 로그인 화면에서 이유 없이 한 번 더 막힌다.
            throw invalidRefreshToken(httpResponse);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, webTokenDelivery.issueCookie(response.refreshToken()))
                .body(response.withoutRefreshToken());
    }

    /** 서버의 refresh token 폐기 (계약서 §3, v1.7.0). 멱등이라 항상 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@LoginUser User user, HttpServletRequest http) {
        if (webTokenDelivery.isWeb(http)) {
            webTokenDelivery.requireTrustedOrigin(http);
        }
        authService.logout(user);
        if (!webTokenDelivery.isWeb(http)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, webTokenDelivery.clearCookie())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@LoginUser User user) {
        return ResponseEntity.ok(authService.me(user));
    }

    /**
     * 웹이면 refresh token 을 본문에서 빼고 쿠키로 옮긴다 (계약서 §3, v1.32.0).
     * 앱이면 받은 그대로 내보낸다.
     */
    private ResponseEntity<AuthResponse> withTokenDelivery(AuthResponse response,
                                                           HttpServletRequest http,
                                                           HttpStatus status) {
        if (!webTokenDelivery.isWeb(http)) {
            return ResponseEntity.status(status).body(response);
        }
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, webTokenDelivery.issueCookie(response.refreshToken()))
                .body(response.withoutRefreshToken());
    }

    /**
     * 앱 경로의 본문 검사. 본문 자체가 없으면 400 이다 — 전에는
     * {@code @RequestBody} 가 필수라 스프링이 냈고, 웹을 위해 optional 로 바꾸면서
     * 여기로 내려왔다. <b>같은 요청에 같은 응답이 나가야 한다.</b>
     */
    private String requireBodyToken(RefreshRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, List.of(
                    new ErrorResponse.FieldError("refreshToken", "refresh token 은 필수입니다.")));
        }
        return request.refreshToken();
    }

    /** 401 과 함께 쿠키를 지운다. 헤더를 먼저 붙여 두면 예외 처리기가 바꾸지 않는다. */
    private BusinessException invalidRefreshToken(HttpServletResponse httpResponse) {
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, webTokenDelivery.clearCookie());
        return new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
    }
}
