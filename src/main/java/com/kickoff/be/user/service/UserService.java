package com.kickoff.be.user.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.common.Patchable;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.dto.PasswordChangeRequest;
import com.kickoff.be.user.dto.PasswordVerifyRequest;
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
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

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
        // 유니크 규칙 (계약서 §3, v1.16.0). 자기 자신의 기존 값은 중복이 아니다 —
        // 안 그러면 닉네임을 그대로 두고 전화번호만 고치는 요청이 자기 닉네임에 걸린다.
        if (Patchable.isPresent(request.nickname())
                && userRepository.existsByNicknameIgnoreCaseAndIdNot(
                        Patchable.valueOf(request.nickname()), user.getId())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (Patchable.isPresent(request.phone())
                && userRepository.existsByPhoneAndIdNot(
                        Patchable.valueOf(request.phone()), user.getId())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
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

    /**
     * 로그인 상태의 비밀번호 변경 (계약서 §3-3 아래, v1.24.0). 재설정(§3-3)과 별개 경로다.
     *
     * <b>refresh token 을 폐기하지 않는다.</b> 재설정과 정반대인데 이유가 다르기 때문이다 —
     * 재설정은 <b>탈취 대응</b>이라 남의 기기에 살아 있는 세션을 끊는 게 목적이고, 이쪽은
     * 본인이 방금 현재 비밀번호를 댄 <b>일상 변경</b>이다. 여기서 폐기하면 비밀번호를
     * 바꿨다는 이유로 자기 앱에서 튕겨 나간다.
     *
     * 소셜 계정은 400 이다. 401 도 403 도 아닌 이유는 "권한이 없다"가 아니라 <b>이 계정에는
     * 바꿀 비밀번호가 없다</b>는 뜻이기 때문이다. FE 가 메뉴를 이메일 계정에만 노출하므로
     * 정상 경로에서는 생기지 않는다.
     */
    @Transactional
    public void changePassword(User loginUser, PasswordChangeRequest request) {
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireCurrentPassword(user, request.currentPassword(),
                "소셜 계정은 비밀번호를 변경할 수 없습니다.");
        user.updatePassword(passwordEncoder.encode(request.newPassword()));
    }

    /**
     * 본인 확인용 비밀번호 검증 (계약서 §3-4 위, v1.24.1). <b>상태를 바꾸지 않는다.</b>
     *
     * 계정 관리 화면(탈퇴·비밀번호 변경이 있는 곳)의 진입 게이트다. FE 가 진입할 때마다
     * 묻고 캐시하지 않는다 — 서버는 그 정책을 강제할 수 없고, 여기서 할 일은 "맞는가"에
     * 정직하게 답하는 것뿐이다.
     *
     * <b>{@code readOnly = true} 가 방어의 일부다.</b> 성능 때문이 아니라, 이 메서드에
     * 실수로 쓰기가 들어와도 <b>플러시가 일어나지 않아 반영되지 않기</b> 때문이다.
     * 변이 검증으로 확인했다 — 여기에 {@code clearRefreshToken()} 을 넣어도 테스트가
     * 하나도 안 깨지는데, readOnly 를 함께 떼면 그때 깨진다. 즉 구조와 테스트가 두 겹으로
     * 막고 있고, <b>둘 중 하나만 사라져도 나머지가 잡는다.</b>
     */
    @Transactional(readOnly = true)
    public void verifyPassword(User loginUser, PasswordVerifyRequest request) {
        User user = userRepository.findById(loginUser.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireCurrentPassword(user, request.password(),
                "소셜 계정은 비밀번호가 없습니다.");
    }

    /**
     * 비밀번호 변경과 본인 확인이 <b>같은 규칙</b>을 쓴다 — 소셜은 400 VALIDATION_FAILED,
     * 불일치는 400 PASSWORD_MISMATCH.
     *
     * 한 곳에 모은 이유: 두 API 가 사용자에게는 "비밀번호를 다시 대는" 같은 행동이라,
     * 응답이 갈리면 화면이 이유 없이 달라진다. 나뉘어 있으면 한쪽만 고쳐지는 날이 온다.
     *
     * <b>불일치는 400 이지 401 이 아니다</b> — 탈퇴(§3-4)와 같은 이유다. 401 을 주면 FE
     * 인터셉터가 세션 만료로 오인해 refresh 를 타고, 사용자는 비밀번호를 틀린 줄도
     * 모른 채 화면이 튄다.
     *
     * 형식 규칙은 걸지 않는다. v1.16.0 이전 규칙으로 만들어진 계정이 실재하는데, 형식으로
     * 먼저 거절하면 그 사람들은 <b>계정 관리에 들어갈 수조차 없다.</b>
     */
    private void requireCurrentPassword(User user, String password, String socialMessage) {
        if (!user.hasPassword()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, socialMessage);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }

    private UserResponse toResponse(User user) {
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        return UserResponse.of(user, team,
                socialAccountRepository.findProvidersByUserId(user.getId()));
    }
}
