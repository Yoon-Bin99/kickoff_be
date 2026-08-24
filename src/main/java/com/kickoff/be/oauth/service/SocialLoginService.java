package com.kickoff.be.oauth.service;

import com.kickoff.be.auth.jwt.JwtTokenProvider;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.dto.OAuthProfile;
import com.kickoff.be.oauth.entity.AuthProvider;
import com.kickoff.be.oauth.entity.SocialAccount;
import com.kickoff.be.oauth.repository.SocialAccountRepository;
import com.kickoff.be.user.entity.User;
import com.kickoff.be.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계약서 §3-1 의 계정 결정 규칙. 제공자 통신과 분리해 둔 이유는 두 가지다 —
 * 외부 HTTP 왕복을 트랜잭션 안에 넣지 않으려는 것과, 이 규칙만 따로 테스트하려는 것.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final String FALLBACK_NICKNAME = "킥오프사용자";
    private static final int NICKNAME_MIN = 2;
    private static final int NICKNAME_MAX = 20;
    private static final int MAX_SUFFIX = 1000;

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final JwtTokenProvider tokenProvider;

    public record SocialLoginResult(String accessToken, boolean newUser) {
    }

    @Transactional
    public SocialLoginResult login(AuthProvider provider, OAuthProfile profile) {
        // 규칙 1 — 연동 이력이 있으면 그 계정이다. 이메일 동의를 나중에 철회해도
        // 이미 연동된 사용자는 계속 로그인할 수 있어야 해서 이메일 검사보다 앞에 둔다.
        Optional<SocialAccount> linked = socialAccountRepository
                .findByProviderAndProviderUserId(provider, profile.providerUserId());
        if (linked.isPresent()) {
            return issue(linked.get().getUser(), false);
        }

        // 규칙 4 — 이메일이 없으면 계정을 찾을 수도 만들 수도 없다.
        if (!profile.hasEmail()) {
            throw new BusinessException(ErrorCode.EMAIL_CONSENT_REQUIRED);
        }

        // 규칙 2 — 이메일이 같으면 자동 연동. 카카오·네이버가 검증된 이메일만 주기 때문에
        // 허용하는 것이다. 검증되지 않은 이메일을 주는 제공자를 붙일 때 이 분기를 그대로
        // 두면 남의 계정을 가로챌 수 있다 (계약서 §3-1 경고).
        Optional<User> existing = userRepository.findByEmail(profile.email());
        if (existing.isPresent()) {
            link(existing.get(), provider, profile);
            log.info("소셜 계정 자동 연동 — userId={}, provider={}", existing.get().getId(), provider);
            return issue(existing.get(), false);
        }

        // 규칙 3 — 신규 생성. 비밀번호도 전화번호도 없다.
        User created = userRepository.save(User.builder()
                .email(profile.email())
                .password(null)
                .nickname(uniqueNickname(profile.nickname()))
                .phone(null)
                .build());
        link(created, provider, profile);
        return issue(created, true);
    }

    private SocialLoginResult issue(User user, boolean newUser) {
        return new SocialLoginResult(tokenProvider.createToken(user.getId()), newUser);
    }

    private void link(User user, AuthProvider provider, OAuthProfile profile) {
        socialAccountRepository.save(SocialAccount.builder()
                .user(user)
                .provider(provider)
                .providerUserId(profile.providerUserId())
                .email(profile.email())
                .build());
    }

    /**
     * 제공자 닉네임을 그대로 쓰되 겹치면 뒤에 숫자를 붙인다 (계약서 §3-1 규칙 3).
     * 닉네임에 유니크 제약은 걸지 않는다 — 이메일 가입에는 원래 없던 제약이라
     * 여기서 만들면 기존 사용자와 시드가 규칙을 소급해 위반하게 된다.
     */
    private String uniqueNickname(String raw) {
        String base = normalize(raw);
        if (!userRepository.existsByNickname(base)) {
            return base;
        }
        for (int suffix = 2; suffix < MAX_SUFFIX; suffix++) {
            String tail = String.valueOf(suffix);
            String candidate = trimTo(base, NICKNAME_MAX - tail.length()) + tail;
            if (!userRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        // 1000개가 겹치는 일은 없겠지만, 없다고 단정하고 중복을 내보내지는 않는다
        return trimTo(base, NICKNAME_MAX - 13) + System.currentTimeMillis();
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return FALLBACK_NICKNAME;
        }
        String trimmed = trimTo(raw.trim(), NICKNAME_MAX);
        // 계약서가 닉네임을 2~20자로 규정한다. 한 글자짜리 프로필이 오면 기본값으로 돌린다.
        return trimmed.length() < NICKNAME_MIN ? FALLBACK_NICKNAME : trimmed;
    }

    private static String trimTo(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
