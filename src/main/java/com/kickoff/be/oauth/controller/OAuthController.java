package com.kickoff.be.oauth.controller;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.oauth.dto.OAuthExchangeRequest;
import com.kickoff.be.oauth.dto.OAuthExchangeResponse;
import com.kickoff.be.oauth.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 (계약서 §3-1).
 *
 * 리다이렉트 흐름의 두 엔드포인트(authorize·callback)는 브라우저가 여는 자리라 JSON 이
 * 아니라 302 를 낸다. 세 번째({@code exchange}, v1.26.0)만 FE 코드가 직접 부르는 호출이라
 * JSON 이다 — 웹에서 토큰을 URL 에 싣지 않으려고 더한 경로다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OAuthController {

    /**
     * 허용 목록 밖 복귀 주소를 받았을 때 브라우저에 보여줄 페이지 (계약서 §3-1, v1.26.1).
     *
     * <b>{@code redirect} 값을 본문에도 링크에도 쓰지 않는다.</b> 그 주소는 우리가 신뢰하지
     * 않기로 판단한 값이다 — 링크로 걸면 검사한 의미가 사라지고(open redirect), 그대로
     * 찍으면 스크립트가 섞여 들어올 수 있다. 그래서 "무엇이 잘못됐는지"만 말하고 값은
     * 되비추지 않는다. 되돌아가는 것도 {@code history.back()} 으로만 한다.
     *
     * <b>외부 자원을 하나도 부르지 않는다.</b> 폰트·이미지·스크립트를 부르면 그 요청의
     * {@code Referer} 로 문제의 URL 전체가 제3자에게 나간다 — 웹 OAuth 를 코드 교환으로
     * 바꾼 것과 같은 이유다.
     *
     * 리소스 파일이 아니라 코드 상수로 둔다. 자바 소스는 빌드가 UTF-8 로 읽는 것이
     * 고정돼 있어서(build.gradle) 한글이 깨질 자리가 없다.
     */
    private static final String REJECTED_REDIRECT_PAGE = """
            <!doctype html>
            <html lang="ko">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>돌아갈 수 없는 주소입니다</title>
            <style>
            body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
            background:#f7f7f8;color:#1b1b1f;
            font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",system-ui,sans-serif}
            main{max-width:22rem;padding:2rem;text-align:center}
            p{margin:0 0 1.5rem;font-size:1rem;line-height:1.6}
            button{padding:.75rem 1.5rem;font-size:1rem;color:#fff;background:#1B7F4B;
            border:0;border-radius:.5rem;cursor:pointer}
            </style>
            </head>
            <body>
            <main>
            <p>허용되지 않은 복귀 주소입니다. 앱이나 사이트로 돌아가 다시 시도해 주세요.</p>
            <button type="button" onclick="history.back()">뒤로 가기</button>
            </main>
            </body>
            </html>
            """;

    private final OAuthService oauthService;

    /**
     * 웹이 받은 일회용 코드를 토큰으로 바꾼다 (계약서 §3-1, v1.26.0). 인증 불필요.
     *
     * <b>이 흐름에서 유일하게 JSON 을 내는 엔드포인트다.</b> 나머지 둘은 브라우저가 여는
     * 자리라 302 를 내지만, 이건 FE 코드가 직접 부르는 호출이다.
     *
     * POST 인 이유는 두 가지다. 코드를 쓰면 없어지므로 안전하지 않고(GET 은 안전해야 한다),
     * 무엇보다 <b>본문으로 받아야 코드가 접근 로그에 안 남는다</b> — 토큰을 URL 에서 빼내려고
     * 만든 절차라 코드를 다시 URL 에 두면 앞뒤가 안 맞는다.
     */
    @PostMapping("/api/auth/oauth/exchange")
    public ResponseEntity<OAuthExchangeResponse> exchange(
            @Valid @RequestBody OAuthExchangeRequest request) {
        return ResponseEntity.ok(OAuthExchangeResponse.of(oauthService.exchange(request.code())));
    }

    /**
     * FE 가 브라우저로 여는 진입점. 인증 불필요.
     *
     * <b>허용 목록 밖 redirect 의 400 은 브라우저에게 HTML 로 준다</b> (계약서 §3-1, v1.26.1).
     * 이 API 는 항상 브라우저가 여는 자리다 — 앱은 인앱 브라우저, 웹은 페이지째 이동이다.
     * 그래서 JSON 400 이 나가면 사용자는 API 도메인에서 <b>날 JSON 을 보고 갇힌다.</b>
     * 돌아갈 링크조차 없다(그 redirect 는 우리가 신뢰하지 않기로 판단한 주소다).
     *
     * 여기서만 처리하고 전역 예외 처리기는 건드리지 않는다. 다른 API 의 오류는 전부 FE
     * 코드가 받아 처리하므로 JSON 이 맞고, 사람이 직접 보는 건 이 하나뿐이다.
     */
    @GetMapping("/api/auth/oauth/{provider}/authorize")
    public ResponseEntity<?> authorize(@PathVariable String provider,
                                       @RequestParam String redirect,
                                       @RequestHeader(value = HttpHeaders.ACCEPT, required = false)
                                       String accept,
                                       HttpServletRequest request) {
        // 성공한 요청은 어디에도 흔적이 안 남아, 사후에 "요청이 오긴 했는지"조차 알 수 없었다.
        log.info("소셜 로그인 진입 — provider={}, origin={}, redirect={}",
                provider, originOf(request), redirect);
        try {
            URI location = oauthService.authorizeUri(provider, redirect, originOf(request));
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
        } catch (BusinessException e) {
            // 여기까지 예외로 올라오는 건 복귀 주소 검사 하나뿐이다 — 그 뒤의 실패는
            // authorizeUri 안에서 302 로 바뀐다. 그래도 코드를 확인하고 넘긴다:
            // 나중에 다른 예외가 새어 나오게 되면 엉뚱한 안내를 보여주게 되기 때문이다.
            if (e.getErrorCode() != ErrorCode.VALIDATION_FAILED || !wantsHtml(accept)) {
                throw e;
            }
            return ResponseEntity.badRequest()
                    .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
                    .body(REJECTED_REDIRECT_PAGE);
        }
    }

    /**
     * 브라우저는 {@code Accept} 에 {@code text/html} 을 넣는다. curl·fetch·테스트는 대개
     * {@code application/json} 이거나 아예 없다.
     *
     * <b>포함 여부만 본다.</b> 브라우저가 보내는 값은
     * {@code text/html,application/xhtml+xml,...} 처럼 길고 q 값도 붙어서, 정확히 파싱하면
     * 브라우저마다 다른 문자열을 상대하게 된다. 여기서 필요한 판단은 "사람이 볼 화면인가"
     * 하나뿐이라 그 정도로 충분하다.
     */
    private static boolean wantsHtml(String accept) {
        return accept != null && accept.toLowerCase(Locale.ROOT).contains(MediaType.TEXT_HTML_VALUE);
    }

    /** 제공자가 되돌려 보내는 자리. FE 는 직접 호출하지 않는다. */
    @GetMapping("/api/auth/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        // code 와 state 는 값 자체가 인증 재료라 유무만 남긴다.
        log.info("소셜 로그인 콜백 — provider={}, code={}, state={}, error={}",
                provider, code == null ? "없음" : "있음", state == null ? "없음" : "있음", error);
        URI location = oauthService.completeLogin(provider, code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    /**
     * 콜백 URI 를 만들 오리진. authorize 는 FE 가 직접 부르는 요청이라 여기서 뽑아도 되고,
     * 그래야 localhost 와 LAN IP 양쪽에서 같은 코드로 동작한다. 뽑은 값은 state 에 실려
     * 콜백까지 전달된다 — 콜백은 제공자가 부르는 요청이라 Host 를 믿지 않는다.
     */
    private static String originOf(HttpServletRequest request) {
        StringBuilder origin = new StringBuilder()
                .append(request.getScheme()).append("://").append(request.getServerName());
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        if (!defaultPort) {
            origin.append(':').append(port);
        }
        return origin.toString();
    }
}
