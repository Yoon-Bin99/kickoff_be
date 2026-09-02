package com.kickoff.be.email.client;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Brevo 메일 발송 어댑터 (계약서 §3-3, v1.23.0 이후 보강).
 *
 * <b>왜 SMTP 가 아니라 HTTP API 인가.</b> Railway 가 아웃바운드 SMTP 를 막는다 —
 * 587 로 TCP 연결 자체가 타임아웃한다(스팸 방지 정책). 인증 이전 단계라 앱 비밀번호나
 * STARTTLS 설정으로 풀 수 없고, 465 도 같은 정책이라 포트만 바꿔서 될 일이 아니다.
 * <b>메일을 HTTPS 로 보내는 것만이 유일한 길이다.</b>
 *
 * 의존성을 안 늘린다 — {@code RestClient} 는 이미 쓰고 있다(카카오 장소 검색·Anthropic).
 *
 * 실패는 그대로 던진다. 호출자(비동기 리스너)가 받아 로그로만 남긴다 — 계약이 "발송
 * 실패도 204"를 요구하고, 실패가 응답에 비치면 그게 곧 계정 존재 신호가 된다.
 */
@Slf4j
public class BrevoEmailClient implements EmailClient {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";
    /** 메일 한 통에 오래 매달릴 이유가 없다. 발송은 비동기라 이 시간이 응답을 안 붙잡는다. */
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;

    public BrevoEmailClient(String apiKey, String senderEmail) {
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public void send(String to, String subject, String body) {
        Map<String, Object> payload = Map.of(
                // Brevo 는 <b>인증된 발신자</b>만 허용한다. 콘솔에서 인증하지 않은 주소를
                // 넣으면 400 이 온다 — 도메인 없이 개인 메일 주소로 인증할 수 있어서
                // 이 단계에 맞는 제공자로 골랐다.
                "sender", Map.of("email", senderEmail),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                // 인증번호 안내라 서식이 필요 없다. HTML 로 보내면 메일 클라이언트마다
                // 렌더가 갈리고, 스팸 판정도 평문이 유리하다.
                "textContent", body);

        restClient.post()
                .uri(URL)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
