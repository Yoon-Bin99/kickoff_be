package com.kickoff.be.user.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.dto.UserUpdateRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final SocialAccountRepository socialAccountRepository;

    /** 닉네임·전화번호 보완 (계약서 §3). 둘 다 optional 이라 null 인 항목은 건드리지 않는다. */
    @Transactional
    public UserResponse update(User loginUser, UserUpdateRequest request) {
        // @LoginUser 로 들어온 인스턴스는 이 트랜잭션에 붙어 있지 않아 변경 감지가 안 걸린다
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updateProfile(request.nickname(), request.phone());
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        return UserResponse.of(user, team,
                socialAccountRepository.findProvidersByUserId(user.getId()));
    }
}
