package com.kickoff.be.stadium.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서울 열린데이터광장 인증키 (계약서 §8-1, v1.27.0).
 *
 * @param key 열린데이터광장에서 발급받는 인증키. 환경변수 {@code SEOUL_OPENDATA_KEY}.
 *            <b>없으면 동기화를 건너뛴다</b> — 수동 시드만으로 목록이 돌아가야 한다는 것이
 *            계약서가 정한 기본 상태다. 키가 없다고 기동이 막히거나 목록이 500 이 되면 안 된다.
 */
@ConfigurationProperties(prefix = "stadium.seoul")
public record SeoulOpenDataProperties(String key) {

    public boolean isConfigured() {
        return key != null && !key.isBlank();
    }
}
