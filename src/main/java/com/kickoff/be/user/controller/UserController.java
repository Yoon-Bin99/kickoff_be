package com.kickoff.be.user.controller;

import com.kickoff.be.auth.jwt.LoginUser;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.dto.UserUpdateRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
