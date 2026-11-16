package com.kickoff.be.operator.service;

import com.kickoff.be.common.BusinessException;
import com.kickoff.be.common.ClientIp;
import com.kickoff.be.common.ErrorCode;
import com.kickoff.be.support.service.OperatorAccounts;
import com.kickoff.be.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 운영자 API 의 문지기 (계약서 §7-2 「운영자 인증」·「감사 기록」, v1.35.1).
 *
 * <b>두 가지를 한 자리에서 한다</b> — 운영자 세션을 확인하고, 그 호출을 접속기록으로
 * 남긴다. 나누면 "검사는 했는데 기록은 안 남는" 경로가 생긴다. 운영자 API 를 하나 더
 * 만들 때 여기 한 줄만 부르면 둘 다 따라온다.
 *
 * <b>검사 순서</b>(계약서 §7-2): 인증(401) → 동의·이용 제한(403, 시큐리티) → 운영자
 * 계정인지(403) → 운영자 세션(401 {@code OPERATOR_SESSION_REQUIRED}). 앞의 둘은 필터가
 * 이미 끝냈고, 여기서는 뒤의 둘만 본다.
 */
@Component
@RequiredArgsConstructor
public class OperatorGuard {

    /** 계약서 §7-2 — 헤더 이름. 바꾸면 FE 가 통째로 막힌다. */
    public static final String HEADER = "X-Operator-Token";

    private final OperatorAccounts operatorAccounts;
    private final OperatorTotpProperties totpProperties;
    private final OperatorSessionService sessionService;
    private final OperatorAuditRecorder auditRecorder;

    /**
     * 운영자 세션을 확인하고 접속기록을 남긴다.
     *
     * @param action       수행한 일 (예: "문의방 열람"). 경로가 아니라 뜻을 적는다
     * @param targetUserId 대상 이용자 — 목록 조회처럼 특정 대상이 없으면 null
     * @param targetId     대상 식별값 (신고 id 등)
     */
    public void require(User user, String action, Long targetUserId, Long targetId) {
        requireOperatorAccount(user);
        requireSession(user);
        record(user, action, targetUserId, targetId);
    }

    /**
     * 운영자 계정인지만 본다 — 세션을 열기 전(OTP 입력)에 쓰는 검사다.
     *
     * <b>운영자가 아니면 403 이다.</b> 404 로 숨기지 않는 것은 FE 가 "운영자 메뉴를
     * 보일지"를 이 응답으로 판정하기 때문이다(계약서 §7-1 의 강등 패턴).
     */
    public void requireOperatorAccount(User user) {
        // 비밀키가 없으면 운영자 API 전체가 403 이다 (계약서 §7-2). 401 이 아닌 이유:
        // OTP 를 입력해도 열리지 않는 상태라, FE 를 OTP 화면으로 보내면 빠져나올 수 없는
        // 고리가 된다. 설정 사고는 사용자가 풀 수 있는 일이 아니다.
        if (!totpProperties.isConfigured()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!operatorAccounts.isOperator(user)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 세션이 없으면 <b>403 이 아니라 401</b> 이다 (계약서 §7-2).
     *
     * FE 는 이 코드를 보고 OTP 입력 화면을 띄운다 — 403 이면 "권한이 없다"로 읽혀
     * 운영자 메뉴 자체가 사라진다.
     */
    public void requireSession(User user) {
        String token = currentRequest() == null ? null : currentRequest().getHeader(HEADER);
        if (!sessionService.isValid(token, user.getId())) {
            throw new BusinessException(ErrorCode.OPERATOR_SESSION_REQUIRED);
        }
    }

    /**
     * 접속기록 한 줄.
     *
     * 실제 저장은 {@link OperatorAuditRecorder} 가 한다 — 같은 빈 안에서 부르면 프록시를
     * 타지 않아 트랜잭션 설정이 통째로 무시되기 때문이다(그 클래스 주석 참고).
     */
    public void record(User user, String action, Long targetUserId, Long targetId) {
        auditRecorder.record(user == null ? null : user.getId(), action, targetUserId,
                targetId, currentIp());
    }

    private String currentIp() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : ClientIp.of(request);
    }

    private HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
