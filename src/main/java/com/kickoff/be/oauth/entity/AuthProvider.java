package com.kickoff.be.oauth.entity;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import java.util.Arrays;

/**
 * 소셜 제공자 (계약서 §1). v1.3 에서 활성은 KAKAO·NAVER 뿐이고 GOOGLE·APPLE 은 값만 예약이다
 * (계약서 §8). 여기 값이 있다고 로그인이 되는 게 아니라, 키가 설정된 제공자만
 * OAuthClientRegistry 에 등록되고 나머지는 UNSUPPORTED_PROVIDER 로 나간다.
 */
public enum AuthProvider {

    KAKAO,
    NAVER,
    GOOGLE,
    APPLE;

    /** 경로값은 소문자다 (`/api/auth/oauth/kakao/...`). 모르는 값은 400 으로 끊는다. */
    public static AuthProvider fromPath(String path) {
        return Arrays.stream(values())
                .filter(provider -> provider.name().equalsIgnoreCase(path))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_PROVIDER));
    }
}
