package com.kickoff.be.user.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.user.dto.PushTokenRequest;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.dto.UserUpdateRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(@LoginUser User user,
                                                 @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(user, request));
    }

    /** Expo push token 등록. body 의 토큰이 null 이면 해제다 (계약서 §8). */
    @PutMapping("/me/push-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePushToken(@LoginUser User user,
                                @Valid @RequestBody PushTokenRequest request) {
        userService.updatePushToken(user, request);
    }
}
