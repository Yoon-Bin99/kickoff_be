package com.kickoff.be.common;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 계약서 "에러 응답 (모든 4xx/5xx 공통)" 형식.
 * fieldErrors 는 검증 실패일 때만 채워지고 그 외에는 빈 배열.
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        OffsetDateTime timestamp,
        List<FieldError> fieldErrors
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                errorCode.getStatus().value(),
                errorCode.name(),
                message,
                // 윈도우에서는 100ns 정밀도라 소수점이 7자리로 나간다. 엄격한 파서를 쓰는
                // 클라이언트가 걸리지 않도록 밀리초까지만 내보낸다.
                OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                fieldErrors == null ? List.of() : fieldErrors
        );
    }
}
