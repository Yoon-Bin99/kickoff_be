package com.kickoff.be.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.kickoff.be.sms.client.SolapiProperties;
import com.kickoff.be.sms.client.SolapiSmsClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 솔라피 어댑터의 <b>요청 구성</b> 검증 (계약서 §3-2). 실제로 보내지 않는다.
 *
 * 이 테스트가 필요한 이유는 인증 실패가 <b>발송 직전에야</b> 드러나기 때문이다. 서명하는
 * 문자열의 순서(date+salt)나 인코딩(16진수)이 틀리면 401 이 오는데, 그건 실기기 앞에서
 * 사용자를 세워 놓고서야 알게 된다. 게다가 이 프로젝트의 문자 잔액은 테스트 15건
 * 남짓이라 "일단 쏴 보고 고치기"를 반복할 여유가 없다.
 *
 * 그래서 서명을 <b>테스트가 독립적으로 다시 계산해</b> 맞춰 본다. 구현을 그대로 베껴
 * 오면 같이 틀려도 통과하므로, 계산 근거는 솔라피 문서의 규칙(date 와 salt 를 이어
 * 붙여 apiSecret 으로 HMAC-SHA256, 소문자 16진수)이다.
 */
class SolapiSmsClientTest {

    private static final Pattern AUTH = Pattern.compile(
            "HMAC-SHA256 apiKey=([^,]+), date=([^,]+), salt=([^,]+), signature=(.+)");

    private static final String API_KEY = "TESTKEY123";
    private static final String API_SECRET = "testsecret456";
    private static final String SENDER = "010-1111-2222";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private SolapiSmsClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SolapiSmsClient(builder,
                new SolapiProperties(API_KEY, API_SECRET, SENDER));
    }

    @Test
    @DisplayName("서명은 date+salt 를 apiSecret 으로 HMAC-SHA256 한 16진수다")
    void signsDatePlusSaltWithSecret() {
        StringBuilder captured = new StringBuilder();
        server.expect(requestTo("https://api.solapi.com/messages/v4/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> captured.append(
                        request.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.send("010-3333-4444", "[킥오프] 인증번호 123456를 입력해 주세요.");
        server.verify();

        Matcher matcher = AUTH.matcher(captured.toString());
        assertThat(matcher.matches()).as("Authorization 헤더 형식").isTrue();
        assertThat(matcher.group(1)).isEqualTo(API_KEY);

        String date = matcher.group(2);
        String salt = matcher.group(3);
        // 순서를 뒤집거나 base64 로 적으면 여기서 갈린다 — 401 을 실기기 앞에서 만나는
        // 대신 여기서 만난다
        assertThat(matcher.group(4)).isEqualTo(hmacHex(date + salt));

        // date 는 파싱 가능한 ISO-8601 이어야 한다. 형식이 어긋나면 솔라피가 거절한다
        assertThat(Instant.parse(date)).isNotNull();
        assertThat(salt).as("salt 는 매번 달라야 한다").isNotBlank();
    }

    @Test
    @DisplayName("salt 는 요청마다 새로 만든다 — 고정이면 재전송 공격에 열린다")
    void saltChangesEveryRequest() {
        String first = captureAuthOf("010-3333-4444");
        String second = captureAuthOf("010-3333-4444");

        assertThat(saltOf(first)).isNotEqualTo(saltOf(second));
    }

    @Test
    @DisplayName("번호는 하이픈을 빼고 보낸다 — 우리 형식 그대로 넘기면 거절된다")
    void stripsHyphensFromNumbers() {
        StringBuilder body = new StringBuilder();
        server.expect(requestTo("https://api.solapi.com/messages/v4/send"))
                .andExpect(request -> body.append(
                        new String(((org.springframework.mock.http.client.MockClientHttpRequest)
                                request).getBodyAsBytes(), StandardCharsets.UTF_8)))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.send("010-3333-4444", "본문");
        server.verify();

        assertThat(body.toString())
                .contains("\"to\":\"01033334444\"")
                .contains("\"from\":\"01011112222\"")
                .contains("\"text\":\"본문\"")
                .doesNotContain("-");
    }

    @Test
    @DisplayName("실패는 예외로 나간다 — 호출자가 502 로 바꾼다")
    void failureIsThrown() {
        server.expect(requestTo("https://api.solapi.com/messages/v4/send"))
                .andRespond(withBadRequest()
                        .body("{\"errorCode\":\"InvalidFrom\",\"errorMessage\":\"발신번호 미등록\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        // 삼키면 사용자는 오지 않는 문자를 3분간 기다린다
        assertThatThrownBy(() -> client.send("010-3333-4444", "본문"))
                .isInstanceOf(RestClientResponseException.class);
    }

    private String captureAuthOf(String phone) {
        RestClient.Builder localBuilder = RestClient.builder();
        MockRestServiceServer localServer = MockRestServiceServer.bindTo(localBuilder).build();
        SolapiSmsClient localClient = new SolapiSmsClient(localBuilder,
                new SolapiProperties(API_KEY, API_SECRET, SENDER));
        StringBuilder captured = new StringBuilder();
        localServer.expect(requestTo("https://api.solapi.com/messages/v4/send"))
                .andExpect(request -> captured.append(
                        request.getHeaders().getFirst("Authorization")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        localClient.send(phone, "본문");
        localServer.verify();
        return captured.toString();
    }

    private String saltOf(String authorization) {
        Matcher matcher = AUTH.matcher(authorization);
        assertThat(matcher.matches()).isTrue();
        return matcher.group(3);
    }

    /** 구현을 베끼지 않고 솔라피 규칙대로 다시 계산한다. */
    private String hmacHex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(API_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
