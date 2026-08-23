package com.kickoff.be.common;

import java.util.List;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 검증 실패를 서비스 레이어에서 던질 때 어떤 필드가 문제인지 같이 넘긴다. */
    private final List<ErrorResponse.FieldError> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, List<ErrorResponse.FieldError> fieldErrors) {
        this(errorCode, errorCode.getMessage(), fieldErrors);
    }

    public BusinessException(ErrorCode errorCode, String message,
                             List<ErrorResponse.FieldError> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }
}
