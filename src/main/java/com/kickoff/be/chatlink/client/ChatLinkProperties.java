package com.kickoff.be.chatlink.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 채팅 서버와의 내부 연결 설정 (계약서 §10, v1.31.0).
 *
 * @param secret 공유 비밀. 환경변수 {@code CHAT_INTERNAL_SECRET}, 양 서버가 <b>같은 값</b>을
 *               가진다. 들어오는 {@code /internal/**} 요청을 확인하는 데도, 나가는 이벤트
 *               헤더에도 같은 값을 쓴다.
 *               <b>비어 있으면 닫힌 쪽으로 실패한다</b> — {@code /internal/**} 전체가 401 이고
 *               이벤트 전달도 멈춘다(행은 쌓인다). 계약서 §10 이 정한 동작이다.
 *               <b>비밀값이다</b> — 문서·저장소·로그에 남기지 않는다.
 * @param url    채팅 서버 주소. 환경변수 {@code CHAT_INTERNAL_URL}, dev 기본
 *               {@code http://localhost:8090}. 비어 있으면 secret 과 같이 전달을 멈춘다 —
 *               보낼 곳을 모르는 채로 재시도만 쌓을 이유가 없다.
 */
@ConfigurationProperties(prefix = "chat.internal")
public record ChatLinkProperties(String secret, String url) {

    /** 공유 비밀이 있는지. 들어오는 요청을 받을지 여기서 갈린다. */
    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }

    /** 이벤트를 내보낼 수 있는지. 주소와 비밀이 둘 다 있어야 한다. */
    public boolean canDeliver() {
        return hasSecret() && url != null && !url.isBlank();
    }
}
