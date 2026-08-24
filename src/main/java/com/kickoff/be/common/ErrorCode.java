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
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."),
    TEAM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 팀을 보유하고 있습니다."),
    TEAM_REQUIRED(HttpStatus.BAD_REQUEST, "팀을 먼저 등록해야 합니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모집글입니다."),
    POST_NOT_OPEN(HttpStatus.CONFLICT, "모집 중인 글이 아닙니다."),
    SELF_REQUEST_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 팀의 글에는 신청할 수 없습니다."),
    DUPLICATE_REQUEST(HttpStatus.CONFLICT, "이미 신청한 글입니다."),
    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 신청입니다."),
    REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 신청입니다."),
    REQUEST_NOT_ACCEPTED(HttpStatus.CONFLICT, "수락된 신청이 아닙니다."),
    REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "아직 리뷰를 쓸 수 없는 매칭입니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 리뷰를 작성했습니다."),

    /** 아래 둘은 계약서 표에는 없지만, 모든 4xx/5xx 가 같은 형식으로 나가야 해서 둔다. */
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
