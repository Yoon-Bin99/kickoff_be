package com.kickoff.be.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 푸시 발송을 요청 스레드에서 떼어내려고 켠다 (계약서 §8).
 *
 * 실행기는 Boot 가 만들어 주는 것을 그대로 쓴다. 지금 비동기로 도는 건 푸시 하나뿐이라
 * 전용 풀을 따로 잡을 이유가 없다 — 종류가 늘면 그때 분리한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
