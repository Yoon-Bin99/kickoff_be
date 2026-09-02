package com.kickoff.be.user.dto;

/**
 * 회원 탈퇴 요청 (계약서 §3-4, v1.23.0).
 *
 * <b>검증 애너테이션이 없는 게 의도다.</b> 소셜 계정은 비밀번호가 없어 body 없이 부르고,
 * 이메일 계정의 비밀번호 확인은 서비스가 한다 — 여기서 {@code @NotBlank} 를 걸면
 * 소셜 사용자가 400 으로 막혀 <b>영영 탈퇴할 수 없다.</b>
 *
 * 형식 규칙도 걸지 않는다. 기존 비밀번호는 v1.16.0 규칙 이전에 만들어졌을 수 있고,
 * 여기서 요구하는 건 "맞는가"뿐이다.
 */
public record AccountDeleteRequest(String password) {
}
