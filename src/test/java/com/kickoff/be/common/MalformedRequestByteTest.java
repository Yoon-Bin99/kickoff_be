package com.kickoff.be.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.kickoff.be.support.StubAiSupportConfig;
import com.kickoff.be.support.StubEmailConfig;
import com.kickoff.be.support.StubOAuthConfig;
import com.kickoff.be.support.StubPlaceConfig;
import com.kickoff.be.support.StubPushConfig;
import com.kickoff.be.support.StubSmsConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 무효 UTF-8 바이트가 들어온 요청이 계약서 §0 형식의 400 으로 나가는지 본다.
 *
 * <b>이 저장소에서 유일하게 실제 서블릿 컨테이너를 띄우는 테스트다. MockMvc 로
 * 옮기지 말 것.</b> 나머지 테스트가 전부 {@code @AutoConfigureMockMvc} 인 데는
 * 이유가 있고(빠르고 컨텍스트가 하나면 된다) 그 관례는 옳다. 다만 MockMvc 는
 * 서블릿 컨테이너의 URI 디코딩 단계를 <b>통째로 우회한다</b> — 요청 라인을 직접
 * 파싱하지 않고 이미 디코딩된 파라미터를 넘겨받는다. 그래서 여기서 잡으려는
 * 버그가 MockMvc 에서는 재현 자체가 안 된다. 실제로 이 버그는 테스트 515 개가
 * 전부 통과하는 동안 운영에서 500 을 내고 있었다.
 *
 * 클라이언트도 {@link java.net.Socket} 으로 바이트를 직접 쓴다. 웬만한 HTTP
 * 클라이언트는 이런 요청을 보내기 전에 정규화하거나 거부해서, 서버에 도달시키려면
 * 이 수준까지 내려가야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({StubOAuthConfig.class, StubPushConfig.class, StubPlaceConfig.class,
        StubSmsConfig.class, StubAiSupportConfig.class, StubEmailConfig.class})
class MalformedRequestByteTest {

    @LocalServerPort
    int port;

    /** %FF 는 어떤 UTF-8 시퀀스에도 나올 수 없는 바이트다. */
    @Test
    @DisplayName("쿼리 스트링의 무효 UTF-8 바이트는 500 이 아니라 400 이다")
    void invalidByteInQueryIsBadRequest() throws Exception {
        Response res = get("/api/posts?region=%FF");

        assertThat(res.status)
                .as("컨테이너가 던진 파싱 예외가 그대로 새면 500 이 된다 (계약서 §0)")
                .isEqualTo(400);
        assertThat(res.body).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /** %EC 는 3 바이트 시퀀스의 첫 바이트다. 뒤가 잘리면 역시 디코딩이 실패한다. */
    @Test
    @DisplayName("쿼리 스트링의 잘린 멀티바이트도 400 이다")
    void truncatedMultibyteInQueryIsBadRequest() throws Exception {
        Response res = get("/api/posts?region=%EC");

        assertThat(res.status).isEqualTo(400);
        assertThat(res.body).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /**
     * 본문 쪽은 이미 정상이다. Jackson 이 못 읽으면
     * {@code HttpMessageNotReadableException} 이 나고 기존 핸들러가 잡는다.
     * 회귀 가드로 함께 둔다 — 쿼리를 고치다가 이쪽을 건드릴 수 있다.
     */
    @Test
    @DisplayName("본문 JSON 의 무효 UTF-8 바이트는 이미 400 이다")
    void invalidByteInBodyIsBadRequest() throws Exception {
        byte[] body = {
                '{', '"', 'e', 'm', 'a', 'i', 'l', '"', ':', '"', (byte) 0xFF, '"', ',',
                '"', 'p', 'a', 's', 's', 'w', 'o', 'r', 'd', '"', ':', '"', 'x', '"', '}'
        };
        Response res = post("/api/auth/login", body);

        assertThat(res.status).isEqualTo(400);
        assertThat(res.body).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /**
     * 대조군. 무효 바이트를 400 으로 돌리다가 <b>멀쩡한 한글까지 400 으로 막으면</b>
     * 지역 필터가 통째로 죽는다. 이 테스트가 그걸 막는다.
     */
    @Test
    @DisplayName("정상 UTF-8 한글 쿼리는 그대로 200 이다")
    void validKoreanQueryStillWorks() throws Exception {
        // %EC%84%9C%EC%9A%B8 = "서울"
        Response res = get("/api/posts?region=%EC%84%9C%EC%9A%B8");

        assertThat(res.status).isEqualTo(200);
    }

    // ── raw HTTP

    private record Response(int status, String body) { }

    private Response get(String target) throws IOException {
        return send("GET " + target + " HTTP/1.1", null);
    }

    private Response post(String target, byte[] body) throws IOException {
        return send("POST " + target + " HTTP/1.1", body);
    }

    private Response send(String requestLine, byte[] body) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);

            StringBuilder head = new StringBuilder()
                    .append(requestLine).append("\r\n")
                    .append("Host: 127.0.0.1:").append(port).append("\r\n")
                    .append("Connection: close\r\n");
            if (body != null) {
                head.append("Content-Type: application/json\r\n")
                        .append("Content-Length: ").append(body.length).append("\r\n");
            }
            head.append("\r\n");

            OutputStream out = socket.getOutputStream();
            // 요청 라인은 ASCII 다. 무효 바이트는 %FF 처럼 퍼센트 인코딩으로 실려 가고,
            // 그걸 바이트로 되돌리는 것은 서버 쪽 디코딩 단계다 — 거기가 이 테스트의 대상이다.
            out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
            if (body != null) {
                out.write(body);
            }
            out.flush();

            String raw = readAll(socket.getInputStream());
            return new Response(parseStatus(raw), payloadOf(raw));
        }
    }

    private String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /** "HTTP/1.1 400 " 의 가운데 숫자. 못 읽으면 -1 로 두어 단언에서 드러나게 한다. */
    private int parseStatus(String raw) {
        String[] parts = raw.split(" ", 3);
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String payloadOf(String raw) {
        int separator = raw.indexOf("\r\n\r\n");
        return separator < 0 ? "" : raw.substring(separator + 4);
    }
}
