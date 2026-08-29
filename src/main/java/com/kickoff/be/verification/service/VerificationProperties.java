package com.kickoff.be.verification.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param phoneRequired signup·PATCH 의 전화번호에 verificationToken 을 <b>강제</b>할지
 *                      (계약서 §3-2, 기본 false).
 *                      <p>
 *                      false 라고 검증을 안 하는 게 아니다 — 토큰이 실려 오면 검증하고
 *                      무효면 400 이다. "없어도 통과"와 "있으면 검증"은 다른 이야기다.
 *                      무효 토큰을 조용히 삼키면, 켜는 날 갑자기 가입이 막히는 사용자가
 *                      생기고 그때는 원인을 찾기 어렵다.
 *                      <p>
 *                      스위치를 둔 이유는 롤아웃 순서다. 토큰 없이 가입하는 구버전 앱이
 *                      아직 돌고 있어서, 서버를 먼저 배포해도 깨지지 않아야 한다.
 *                      순서: BE 배포 → FE 배포 → preview 재빌드·배포 → true 전환.
 */
@ConfigurationProperties(prefix = "kickoff.verification")
public record VerificationProperties(boolean phoneRequired) {
}
