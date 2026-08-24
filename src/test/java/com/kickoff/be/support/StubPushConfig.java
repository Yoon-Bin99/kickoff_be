package com.kickoff.be.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * 푸시 발송을 테스트에서 관측 가능하게 만든다.
 *
 * 실행기를 동기로 바꾸는 게 핵심이다. 운영에서는 @Async 로 다른 스레드에서 보내는데,
 * 그대로 두면 테스트가 "아직 안 왔을 뿐인지 안 온 것인지" 구분할 수 없어 비결정적이 된다.
 * 대신 <b>"비동기라 응답을 붙잡지 않는다"는 성질은 테스트가 아니라 코드 구조로만 보장된다</b> —
 * 이 절충은 supervisor 승인 아래 택했다.
 *
 * AFTER_COMMIT 타이밍은 동기 실행기에서도 그대로 지켜지므로, 커밋 이후 발송이라는
 * 더 중요한 성질은 여전히 테스트가 검증한다.
 */
@TestConfiguration
public class StubPushConfig {

    @Bean
    @Primary
    public StubPushClient stubPushClient() {
        return new StubPushClient();
    }

    @Bean
    @Primary
    public TaskExecutor syncTaskExecutor() {
        return new SyncTaskExecutor();
    }
}
