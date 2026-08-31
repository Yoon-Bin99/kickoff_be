package com.kickoff.be.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ../docs/api-contract.md 의 에러 코드 표와 1:1로 대응한다.
 * 코드/상태를 바꿔야 하면 계약서부터 고칠 것.
 */
@Getter
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    /**
     * access 만료(UNAUTHORIZED)와 반드시 구분해야 한다 (계약서 §3, v1.7.0).
     * FE 는 이 코드일 때만 로그인 화면으로 보낸다 — UNAUTHORIZED 는 refresh 를 한 번
     * 시도해 볼 신호이고, 이건 그 시도마저 실패했다는 뜻이다.
     */
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."),
    /** 팀원 명단은 30명까지 (계약서 §4-1, v1.8.0). */
    TEAM_MEMBER_LIMIT(HttpStatus.BAD_REQUEST, "팀원은 30명을 넘을 수 없습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀원입니다."),
    RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 경기 기록입니다."),
    /** 매칭에서 기록 만들기 (계약서 §4-1, v1.10.0). */
    RECORD_NOT_AVAILABLE(HttpStatus.CONFLICT, "아직 전적을 기록할 수 없는 경기입니다."),
    RECORD_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 이 경기의 전적을 기록했습니다."),
    /** 팀 관리자 (계약서 §4-2, v1.9.0). */
    ALREADY_TEAM_ADMIN(HttpStatus.CONFLICT, "이미 이 팀의 관리자입니다."),
    TEAM_ADMIN_LIMIT(HttpStatus.BAD_REQUEST, "관리자는 5명을 넘을 수 없습니다."),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "이 팀의 관리자가 아닙니다."),
    /** 팀 소속·가입 (계약서 §4-3, v1.11.0). */
    JOIN_ALREADY_REQUESTED(HttpStatus.CONFLICT, "이미 가입 신청이 대기 중입니다."),
    ALREADY_TEAM_MEMBER(HttpStatus.CONFLICT, "이미 이 팀 소속입니다."),
    JOIN_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 가입 신청입니다."),
    JOIN_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 가입 신청입니다."),
    TEAM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 팀을 보유하고 있습니다."),
    TEAM_REQUIRED(HttpStatus.BAD_REQUEST, "팀을 먼저 등록해야 합니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모집글입니다."),
    POST_NOT_OPEN(HttpStatus.CONFLICT, "모집 중인 글이 아닙니다."),
    SELF_REQUEST_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 팀의 글에는 신청할 수 없습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 신청한 글입니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 신청입니다."),
    REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    REQUEST_NOT_ACCEPTED(HttpStatus.CONFLICT, "수락된 신청이 아닙니다."),
    MATCH_CANCEL_EXPIRED(HttpStatus.CONFLICT, "이미 지난 경기는 매칭을 취소할 수 없습니다."),
    /** 매칭 채팅 (계약서 §6-1, v1.12.0). 경기 시각이 지나면 읽기 전용이다. */
    CHAT_CLOSED(HttpStatus.CONFLICT, "경기 시각이 지나 메시지를 보낼 수 없습니다."),
    REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "아직 리뷰를 쓸 수 없는 매칭입니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 작성했습니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인입니다."),
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),
    /** v1.3.4 에서 자동 연동이 폐지돼 지금은 쓰지 않는다. 연동 기능이 생기는 v2 를 위해 남긴다. */
    EMAIL_CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "이메일 제공에 동의해야 가입할 수 있습니다."),
    PHONE_REQUIRED(HttpStatus.BAD_REQUEST, "전화번호를 먼저 등록해야 합니다."),
    PLACE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "장소 검색에 실패했습니다."),

    /** 가입 폼 강화 (계약서 §3, v1.16.0). 이메일 중복은 예전부터 EMAIL_ALREADY_EXISTS 다. */
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 전화번호입니다."),

    /** 전화번호 문자 인증 (계약서 §3-2, v1.15.0). */
    PHONE_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "전화번호 인증이 필요합니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),
    VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다. 다시 받아 주세요."),
    VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "인증번호를 너무 자주 요청했습니다. 잠시 후 다시 시도해주세요."),
    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "인증번호 발송에 실패했습니다."),

    /** 아래 둘은 계약서 표에는 없지만, 모든 4xx/5xx 가 같은 형식으로 나가야 해서 둔다. */
    /**
     * 경로는 있는데 메서드가 틀린 경우 (계약서 §0, v1.12.1).
     *
     * NOT_FOUND 와 같은 취지다 — 클라이언트 실수를 서버 장애로 오인하지 않게 한다.
     * 실제로 FE 가 accept 를 PATCH 로 부르고 500 을 받아 서버 회귀로 판단한 일이 있었다.
     */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
