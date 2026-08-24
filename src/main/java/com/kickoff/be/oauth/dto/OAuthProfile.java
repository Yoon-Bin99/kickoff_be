package com.kickoff.be.oauth.dto;

/**
 * 제공자 프로필을 한 형태로 정규화한 결과. 카카오와 네이버의 응답 구조가 서로 달라서,
 * 계정 결정 로직이 제공자별 JSON 모양을 알지 않도록 여기서 끊는다.
 *
 * @param providerUserId 제공자 고유 식별자. 필수
 * @param email          제공자가 준 이메일. 동의를 거부하면 null 이고, 그때는
 *                       EMAIL_CONSENT_REQUIRED 로 로그인을 중단한다 (계약서 §3-1 규칙 4)
 * @param nickname       제공자 프로필 닉네임. 없으면 null 이고 기본값으로 대체한다
 */
public record OAuthProfile(String providerUserId, String email, String nickname) {

    public boolean hasEmail() {
        return email != null && !email.isBlank();
    }
}
