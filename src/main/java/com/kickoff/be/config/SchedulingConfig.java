package com.kickoff.be.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 주기 작업을 켠다. 지금 도는 것은 보관 기간 파기 하나뿐이다
 * ({@code RetentionCleanupJob} — 개인정보처리방침 §3-2).
 *
 * <b>test 프로파일에서는 켜지 않는다.</b> 통합 테스트가 컨텍스트를 여러 번 띄우는데,
 * 그때마다 스케줄러가 함께 뜨면 테스트 도중 예고 없이 삭제가 도는 일이 생긴다. 새벽
 * 4시 30분 크론이라 실제로 걸릴 확률은 낮지만, "낮다"에 기대는 테스트는 언젠가 깨지고
 * 그때 원인을 찾기가 아주 어렵다. 파기 로직 자체는 잡 메서드를 직접 불러서 검증한다.
 *
 * {@code AsyncConfig} 와 나누어 둔 이유는 성격이 달라서다. 비동기는 요청 처리를 떼어내는
 * 것이고 스케줄링은 요청 없이 도는 것이라, 한쪽을 끄고 싶을 때 다른 쪽이 딸려 오면 안 된다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
