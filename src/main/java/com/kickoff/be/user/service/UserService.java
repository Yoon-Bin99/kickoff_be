package com.kickoff.be.user.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.dto.PushTokenRequest;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.dto.UserUpdateRequest;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.team.repository.TeamMemberRepository;
import com.kickoff.be.user.repository.UserRepository;
import com.kickoff.be.verification.service.PhoneVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PhoneVerificationService verificationService;

    /**
     * 프로필 보완 (계약서 §3). 전부 optional 이고, 안 보낸 필드는 그대로다.
     *
     * 활동 지역만 명시적 null 로 지울 수 있다 — 지우면 전국이 된다 (계약서 §2, v1.6.0).
     * 닉네임·전화번호는 빈 상태가 의미를 갖지 않아서 null 을 보내면 400 이다. 조용히
     * 무시하면 200 이 돌아가 FE 가 반영된 줄 알고 넘어간다.
     */
    @Transactional
    public UserResponse update(User loginUser, UserUpdateRequest request) {
        // @LoginUser 로 들어온 인스턴스는 이 트랜잭션에 붙어 있지 않아 변경 감지가 안 걸린다
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Patchable.rejectClear(request.nickname(), "nickname");
        Patchable.rejectClear(request.phone(), "phone");
        // 전화번호를 넣거나 바꾸는 요청만 인증을 탄다 (계약서 §3-2, v1.15.0).
        // phone 을 건드리지 않는 PATCH 는 예전 그대로다 — 닉네임만 고치는데 문자 인증을
        // 요구하면 소셜 가입자가 프로필을 못 고친다.
        if (Patchable.isPresent(request.phone())) {
            verificationService.consume(Patchable.valueOf(request.phone()),
                    request.verificationToken());
        }
        user.updateProfile(Patchable.valueOf(request.nickname()),
                Patchable.valueOf(request.phone()));
        syncRosterNames(user, Patchable.valueOf(request.nickname()));
        if (Patchable.isPresent(request.activityRegion())) {
            user.updateActivityRegion(Patchable.valueOf(request.activityRegion()));
        }
        return toResponse(user);
    }

    /**
     * 닉네임이 바뀌면 계정이 연결된 명단 항목의 이름도 따라간다 (계약서 §4-3, v1.11.0).
     *
     * 안 맞춰 주면 명단에 옛 이름이 남아 같은 사람이 두 명처럼 보인다. 명단 쪽에서 이름을
     * 못 고치게 막아 놨으므로(400), 여기서 따라가지 않으면 영영 어긋난 채로 남는다.
     *
     * 한 사람이 여러 팀에 속할 수 있어 항목이 여러 개일 수 있다.
     */
    private void syncRosterNames(User user, String newNickname) {
        if (newNickname == null) {
            return;
        }
        teamMemberRepository.findByUser_Id(user.getId())
                .forEach(member -> member.syncNameFromAccount(newNickname));
    }

    /**
     * Expo push token 등록·해제 (계약서 §8). 사용자당 하나라 마지막 등록이 이긴다.
     * 같은 값을 다시 넣어도, null 로 지워도 결과는 204 로 같다 — 멱등이다.
     */
    @Transactional
    public void updatePushToken(User loginUser, PushTokenRequest request) {
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.updatePushToken(request.expoPushToken());
    }

    private UserResponse toResponse(User user) {
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        return UserResponse.of(user, team,
                socialAccountRepository.findProvidersByUserId(user.getId()));
    }
}
