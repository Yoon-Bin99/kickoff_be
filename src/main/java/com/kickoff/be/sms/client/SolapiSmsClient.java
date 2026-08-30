package com.kickoff.be.sms.client;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 솔라피(쿨SMS 통합 플랫폼)로 문자를 보낸다 (계약서 §3-2).
 *
 * 이 어댑터는 <b>키·발신번호가 모두 있을 때만</b> 등록된다 (SmsClientConfig). 없으면
 * LoggingSmsClient 가 남아, SMS_ENABLED=true 인데 보낼 수단이 없는 상태를 502 로
 * 시끄럽게 알린다.
 *
 * <b>실패하면 예외를 던진다.</b> 푸시와 달리 인증번호는 그 발송 자체가 요청의 목적이라,
 * 못 보냈는데 204 를 주면 사용자는 오지 않는 문자를 3분간 기다린다. 호출자가 이 예외를
 * 502 SMS_SEND_FAILED 로 바꾼다.
 */
@Slf4j
public class SolapiSmsClient implements SmsClient {

    private static final String SEND_URL = "https://api.solapi.com/messages/v4/send";

    /** 서명에 쓰는 시각. 솔라피가 ISO-8601 UTC 를 요구한다. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_INSTANT;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SALT_BYTES = 16;

    private final RestClient restClient;
    private final SolapiProperties properties;
    private final SecureRandom random = new SecureRandom();

    public SolapiSmsClient(RestClient.Builder builder, SolapiProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    @Override
    public void send(String phone, String text) {
        try {
            restClient.post()
                    .uri(SEND_URL)
                    .header("Authorization", authorization())
                    .body(body(phone, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 솔라피는 실패 이유를 본문의 errorCode/errorMessage 로 준다 (잔액 부족,
            // 미등록 발신번호, 서명 불일치 등). 상태 코드만 남기면 어느 쪽인지 알 수 없어
            // 실기기 앞에서 원인을 못 찾는다.
            log.error("솔라피 발송 실패 — status={} body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        }
    }

    /**
     * {@code HMAC-SHA256 apiKey=..., date=..., salt=..., signature=...}
     *
     * 서명은 <b>date 와 salt 를 이어 붙인 문자열</b>을 apiSecret 으로 HMAC-SHA256 한 뒤
     * 소문자 16진수로 적는다. 순서를 바꾸거나 인코딩을 base64 로 하면 401 이 오는데,
     * 그건 발송 직전에야 드러나므로 여기 적어 둔다.
     */
    private String authorization() {
        String date = DATE.format(Instant.now());
        String salt = randomSalt();
        return "HMAC-SHA256 apiKey=%s, date=%s, salt=%s, signature=%s"
                .formatted(properties.apiKey(), date, salt, sign(date + salt));
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.apiSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("서명 생성 실패", e);
        }
    }

    private String randomSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * {@code {"message": {"to": ..., "from": ..., "text": ...}}}
     *
     * 번호는 <b>하이픈을 뺀 숫자</b>로 보낸다. 우리 계약의 전화번호 형식은
     * {@code 010-0000-0000} 이라 그대로 넘기면 솔라피가 거절한다.
     */
    private Map<String, Object> body(String phone, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", digitsOf(phone));
        message.put("from", digitsOf(properties.sender()));
        message.put("text", text);
        return Map.of("message", message);
    }

    private String digitsOf(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }
}
