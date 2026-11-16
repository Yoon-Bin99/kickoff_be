package com.kickoff.be.chatlink.client;

import com.kickoff.be.chatlink.dto.ReportContext;
import com.kickoff.be.chatlink.security.InternalSecretFilter;
import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ErrorCode;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * 채팅 메시지를 신고할 때 그 앞뒤 대화를 채팅 서버에서 받아 온다
 * (계약서 §10 {@code GET /internal/chat/rooms/{requestId}/report-context}, v1.35.0).
 *
 * <b>이쪽은 사용자가 기다리는 호출이다.</b> 이벤트 전달({@link ChatServerClient})과 달리
 * 재시도할 기회가 없으므로, 계약이 정한 대로 2초에 끊고 503 으로 답한다 — 더 기다리면
 * 신고 화면이 멈춘 것처럼 보인다.
 *
 * <b>채팅 서버의 판정을 그대로 넘긴다.</b> 그 방의 당사자인지, 신고자가 나가기 전의
 * 메시지인지, 시스템 메시지인지는 채팅 서버만 안다(§10 의 검사 순서). 여기서 다시
 * 판정하면 두 서버의 규칙이 어긋나는 날이 온다.
 */
@Slf4j
@Component
public class ReportContextClient {

    private static final String PATH = "/internal/chat/rooms/%d/report-context";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /** 계약서 §10 — "호출 타임아웃 2초, 무응답·5xx 면 503". */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(2);

    /** 앞뒤로 몇 개씩 받아올지 (계약서 §7-2 — "앞뒤 20개씩"). */
    private static final int WINDOW = 20;

    private final ChatLinkProperties properties;
    private final RestClient restClient;

    public ReportContextClient(ChatLinkProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().requestFactory(requestFactory()).build();
    }

    /**
     * 신고된 메시지와 그 앞뒤 대화.
     *
     * 채팅 서버가 4xx 로 답하면 그 뜻을 사용자에게 그대로 옮긴다 — 404 는 없는 메시지,
     * 403 은 그 방의 당사자가 아님, 400 은 신고할 수 없는 메시지(시스템·내 팀이 보낸 것),
     * 410 은 보관 기간이 지난 방이다.
     */
    public ReportContext fetch(Long requestId, Long messageId, Long reporterUserId) {
        if (!properties.canDeliver()) {
            // 주소나 비밀이 없으면 물어볼 곳이 없다. 신고를 반쪽으로 접수하지 않는다 —
            // 대화 사본이 증거이고, 그것 없이 받아 두면 운영자가 볼 것이 없다.
            log.warn("채팅 서버 연결 설정 없음 — 채팅 메시지 신고를 받을 수 없다");
            throw new BusinessException(ErrorCode.CHAT_UPSTREAM_UNAVAILABLE);
        }
        try {
            return restClient.get()
                    .uri(properties.url() + PATH.formatted(requestId)
                            + "?messageId=%d&reporterUserId=%d&before=%d&after=%d"
                            .formatted(messageId, reporterUserId, WINDOW, WINDOW))
                    .header(InternalSecretFilter.HEADER, properties.secret())
                    .retrieve()
                    .body(ReportContext.class);
        } catch (HttpStatusCodeException e) {
            throw translate(e);
        } catch (RuntimeException e) {
            // 무응답·연결 실패. 신고 자체가 성립하지 않으므로 503 이다.
            log.warn("채팅 신고 문맥 조회 실패 — requestId={}", requestId, e);
            throw new BusinessException(ErrorCode.CHAT_UPSTREAM_UNAVAILABLE);
        }
    }

    /**
     * 채팅 서버의 거절을 우리 에러 코드로 옮긴다 (계약서 §10 의 검사 순서).
     *
     * 5xx 만 503 이다 — 4xx 는 "기다려도 달라지지 않는" 답이라 그대로 전한다.
     */
    private BusinessException translate(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        if (status >= 500) {
            log.warn("채팅 서버 오류 — status={}", status);
            return new BusinessException(ErrorCode.CHAT_UPSTREAM_UNAVAILABLE);
        }
        return switch (status) {
            case 403 -> new BusinessException(ErrorCode.FORBIDDEN);
            case 404 -> new BusinessException(ErrorCode.NOT_FOUND);
            case 410 -> new BusinessException(ErrorCode.CHAT_EXPIRED);
            // 400 과 그 밖의 4xx: 신고할 수 없는 메시지다 (시스템 메시지·내 팀이 보낸 것).
            default -> new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "신고할 수 없는 메시지입니다.");
        };
    }

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }
}
