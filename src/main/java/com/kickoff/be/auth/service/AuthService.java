package com.kickoff.be.auth.service;

import com.kickoff.be.auth.dto.AuthResponse;
import com.kickoff.be.auth.dto.LoginRequest;
import com.kickoff.be.auth.dto.SignupRequest;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone())
                .build());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
        // 소셜로만 가입한 계정은 비밀번호가 없다. 전용 에러 코드를 만들지 않는 이유는
        // 계정이 존재한다는 사실 자체를 알려주지 않기 위해서다 (계약서 §3-1).
        if (!user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(User user) {
        return toUserResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(tokenProvider.createToken(user.getId()), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        return UserResponse.of(user, team,
                socialAccountRepository.findProvidersByUserId(user.getId()));
    }
}
