package com.kickoff.be.push.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled 기본 false. dev 에서 실수로 실기기에 알림이 나가지 않게 한다 (계약서 §8).
 */
@ConfigurationProperties(prefix = "kickoff.push")
public record PushProperties(boolean enabled) {
}
