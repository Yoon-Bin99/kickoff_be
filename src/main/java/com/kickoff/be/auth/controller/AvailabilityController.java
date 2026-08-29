package com.kickoff.be.auth.controller;

import com.kickoff.be.auth.service.AvailabilityService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 폼의 사전 중복 확인 (계약서 §3, v1.16.0). 인증 불필요 — 가입 전에 부르는 API 다.
 *
 * 파라미터 검증에 {@code @Validated} 를 붙이지 않는다. 붙이면 ConstraintViolationException
 * 이 나가 500 이 되는데, 안 붙이면 스프링의 메서드 검증이 HandlerMethodValidationException
 * 을 던지고 그건 이미 400 VALIDATION_FAILED 로 변환된다 (PlaceController 에서 겪은 것과
 * 같은 함정이다).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/availability")
    public ResponseEntity<Map<String, Boolean>> availability(
            @RequestParam(required = false)
            @Email(message = "이메일 형식이 올바르지 않습니다.") String email,

            @RequestParam(required = false)
            @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.") String nickname,

            @RequestParam(required = false)
            @Pattern(regexp = "^010-\\d{4}-\\d{4}$",
                    message = "휴대폰 번호는 010-0000-0000 형식이어야 합니다.") String phone) {
        return ResponseEntity.ok(availabilityService.check(email, nickname, phone));
    }
}
