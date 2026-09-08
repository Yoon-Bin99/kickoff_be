package com.kickoff.be.auth.service;

import com.kickoff.be.auth.dto.AuthResponse;
import com.kickoff.be.auth.dto.LoginRequest;
import com.kickoff.be.auth.dto.RefreshRequest;
import com.kickoff.be.auth.dto.SignupRequest;
import com.kickoff.be.auth.dto.TokenResponse;
import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.auth.jwt.RefreshTokenProvider;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.team.entity.Team;
import com.kickoff.be.team.repository.TeamRepository;
import com.kickoff.be.user.dto.UserResponse;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import com.kickoff.be.verification.service.PhoneVerificationService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Comparator;
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
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenProvider refreshTokenProvider;
    private final PhoneVerificationService verificationService;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        // 유니크 규칙 (계약서 §3, v1.16.0). 폼 순서대로 본다 — FE 가 받은 코드로 해당
        // 입력칸에 포커스를 돌리므로, 여러 개가 겹쳤을 때 위쪽 칸부터 알려주는 게 낫다.
        // 이메일은 대소문자를 무시해 본다 (계약서 §3, v1.16.0). Kim@ 과 kim@ 은 같은
        // 주소이므로 사람이 보기에 같은 계정을 두 개 만들 수 있으면 안 된다.
        //
        // DB 제약은 아직 정확 일치다 — 대소문자만 다른 기존 쌍이 운영에 있을 수 있어
        // 유니크 제약 정비와 함께 미뤘다 (docs/pending/V12 참고). 그래서 지금은
        // "판정은 무시, 제약은 일치"로 어긋나 있는데, 느슨한 쪽이 판정이라 새 중복은
        // 들어오지 않는다.
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByNicknameIgnoreCase(request.nickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
        // 전화번호 인증 (계약서 §3-2, v1.15.0). 이미 있는 이메일을 먼저 걸러내는 순서가
        // 중요하다 — 어차피 실패할 요청에 토큰을 태워 버리면 재시도 때 다시 인증해야 한다.
        verificationService.consume(request.phone(), request.verificationToken());
        User user = userRepository.save(User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .phone(request.phone())
                .activityRegion(request.activityRegion())
                // 요청이 여기까지 왔다는 건 termsAgreed 가 true 라는 뜻이다 — false·누락은
                // 검증에서 400 으로 끊긴다 (계약서 §3, v1.21.0).
                .termsAgreedAt(OffsetDateTime.now())
                .build());
        return toAuthResponse(user);
    }

    /**
     * 로그인 (계약서 §3). v1.7.0 부터 refresh token 을 발급하므로 <b>더 이상 읽기 전용이
     * 아니다</b> — 새 로그인이 이전 기기의 refresh 를 덮어써 무효화한다(단일 기기 정책).
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 한도를 <b>계정 조회보다 먼저</b> 본다. 뒤에 두면 429 가 나오는 조건 자체가
        // "그 계정이 존재하는가"에 걸려, 응답 코드로 가입 여부를 알아낼 수 있게 된다
        // (계약서 §3-1 이 계정 존재를 숨기려고 LOGIN_FAILED 하나로 답하는 것과 같은 이유).
        if (!loginAttemptLimiter.isAllowed(request.email())) {
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED);
        }

        User user = findForLogin(request.email()).orElse(null);
        // 소셜로만 가입한 계정은 비밀번호가 없다. 전용 에러 코드를 만들지 않는 이유는
        // 계정이 존재한다는 사실 자체를 알려주지 않기 위해서다 (계약서 §3-1).
        if (user == null || !user.hasPassword()
                || !passwordEncoder.matches(request.password(), user.getPassword())) {
            // 없는 계정에 대한 시도도 센다. 안 세면 "한도에 걸리는가"로 계정 존재를
            // 물어볼 수 있고, 존재하는 계정만 골라 무제한으로 시도할 수 있게 된다.
            loginAttemptLimiter.recordFailure(request.email());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 맞는 비밀번호를 댔으면 즉시 푼다 — 몇 번 틀렸다는 이유로 본인을 15분 막을
        // 이유가 없다.
        loginAttemptLimiter.reset(request.email());
        return toAuthResponse(user);
    }

    /**
     * 로그인 대상 계정 찾기 (계약서 §3, v1.16.0) — 대소문자를 무시한다.
     *
     * 가입 때 중복 판정이 대소문자를 무시하므로 로그인도 같은 기준이어야 한다. 아니면
     * kim@ 으로 가입한 사람이 Kim@ 으로는 못 들어오는데, 가입을 다시 하려 해도 중복이라
     * 막힌다 — 어느 쪽으로도 못 가는 상태가 된다.
     *
     * <b>정확 일치를 먼저 본다.</b> DB 제약이 아직 정확 일치라 케이스만 다른 두 계정이
     * 이론상 함께 있을 수 있는데, 그때 자기 주소를 정확히 친 사람이 남의 계정으로
     * 들어가면 안 된다. 정확 일치가 없을 때만 무시 검색으로 내려가고, 거기서도 여럿이면
     * 가장 오래된 계정을 고른다 — 무엇을 고르든 한 명이어야 하고, 그 선택이 조회마다
     * 달라지면 안 된다.
     */
    private Optional<User> findForLogin(String email) {
        return userRepository.findByEmail(email)
                .or(() -> userRepository.findAllByEmailIgnoreCase(email).stream()
                        .min(Comparator.comparing(User::getId)));
    }

    @Transactional(readOnly = true)
    public UserResponse me(User user) {
        return toUserResponse(user);
    }

    /**
     * refresh token 으로 새 토큰 쌍을 받는다 (계약서 §3, v1.7.0).
     *
     * 쓸 때마다 로테이션한다 — 새 쌍을 주고 이전 refresh 는 그 자리에서 무효가 된다.
     * 그래서 같은 refresh 를 두 번 쓰면 두 번째는 401 이다.
     *
     * 실패는 전부 INVALID_REFRESH_TOKEN 이다. 없는 토큰인지 만료된 토큰인지 구분해서
     * 알려주지 않는다 — 어느 쪽이든 FE 가 할 일은 재로그인 하나뿐이고, 구분해 주면
     * 토큰 추측에 힌트가 된다.
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = refreshTokenProvider.hash(request.refreshToken());
        User user = userRepository.findByRefreshTokenHash(hash)
                .filter(candidate -> candidate.hasValidRefreshToken(OffsetDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return new TokenResponse(tokenProvider.createToken(user.getId()), issueRefreshToken(user));
    }

    /**
     * 로그아웃 (계약서 §3, v1.7.0). 서버의 refresh token 을 폐기한다.
     *
     * 멱등이다 — 이미 폐기됐어도 204 다. 로그아웃은 실패할 이유가 없어야 한다. 여기서
     * 에러를 내면 FE 는 로컬 토큰을 못 지우고 어정쩡한 상태로 남는다.
     *
     * 이미 발급된 access token 은 만료(1시간)까지 살아 있다. 그걸 즉시 끊으려면 서버가
     * access 도 상태로 들고 있어야 하는데, 그러면 매 요청마다 DB 를 본다. access 를
     * 짧게 잡은 이유가 그 절충이다.
     */
    @Transactional
    public void logout(User loginUser) {
        // @LoginUser 인스턴스는 이 트랜잭션에 붙어 있지 않아 변경 감지가 안 걸린다
        userRepository.findById(loginUser.getId()).ifPresent(user -> {
            user.clearRefreshToken();
            // 푸시 토큰도 함께 지운다. 남겨 두면 <b>로그아웃한 기기가 계속 알림을 받는다</b> —
            // 알림 본문에 상대 팀명과 경기 정보가 들어가므로, 빌려준 폰이나 공용 기기에서
            // 나간 뒤에도 다음 사람이 내 매칭을 계속 보게 된다. "로그아웃하면 이 기기로
            // 알림이 안 온다"가 사용자가 기대하는 동작이기도 하다.
            //
            // 사용자당 토큰이 하나(단일 기기)라 지워도 다른 기기가 잃을 게 없다. 여러 기기를
            // 허용하게 되면 이 줄은 "이 기기의 토큰만" 지우는 형태로 바뀌어야 한다.
            user.updatePushToken(null);
        });
    }

    private AuthResponse toAuthResponse(User user) {
        return new AuthResponse(tokenProvider.createToken(user.getId()),
                issueRefreshToken(user), toUserResponse(user));
    }

    /** 원문은 응답으로만 나가고 서버에는 해시만 남는다 (계약서 §3). */
    private String issueRefreshToken(User user) {
        String rawToken = refreshTokenProvider.issue();
        user.issueRefreshToken(refreshTokenProvider.hash(rawToken),
                refreshTokenProvider.expiresAt());
        return rawToken;
    }

    private UserResponse toUserResponse(User user) {
        Team team = teamRepository.findByOwnerId(user.getId()).orElse(null);
        return UserResponse.of(user, team,
                socialAccountRepository.findProvidersByUserId(user.getId()));
    }
}
