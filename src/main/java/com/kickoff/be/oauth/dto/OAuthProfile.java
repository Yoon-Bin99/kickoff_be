package com.kickoff.be.oauth.dto;

/**
 * 제공자 프로필을 한 형태로 정규화한 결과. 카카오와 네이버의 응답 구조가 서로 달라서,
 * 계정 결정 로직이 제공자별 JSON 모양을 알지 않도록 여기서 끊는다.
 *
 * <b>이메일은 받지 않는다</b> (계약서 §3-1, v1.3.4). 소셜 계정은 언제나 별개 계정이라
 * 이메일로 식별할 일이 없고, 쓰지 않을 개인정보를 굳이 가져오지 않는다.
 *
 * @param providerUserId 제공자 고유 식별자. 연동의 기준이 되는 유일한 값
 * @param nickname       제공자 프로필 닉네임. 없으면 null 이고 기본값으로 대체한다
 */
public record OAuthProfile(String providerUserId, String nickname) {
}
