package com.kickoff.be.auth.dto;

import com.kickoff.be.user.dto.UserResponse;

public record AuthResponse(String accessToken, UserResponse user) {
}
