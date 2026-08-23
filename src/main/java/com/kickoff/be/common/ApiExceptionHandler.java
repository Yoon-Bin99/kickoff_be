package com.kickoff.be.common;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 모든 예외를 계약서의 에러 응답 형식으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code, e.getMessage(), e.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage(), fieldErrors));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerValidation(HandlerMethodValidationException e) {
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED));
    }

    /** 잘못된 열거형 값이나 깨진 JSON 본문. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "요청 본문을 해석할 수 없습니다."));
    }

    /** 쿼리 파라미터에 정의되지 않은 열거형 값이 들어온 경우 등. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        List<ErrorResponse.FieldError> fieldErrors =
                List.of(new ErrorResponse.FieldError(e.getName(), "값의 형식이 올바르지 않습니다."));
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage(), fieldErrors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        List<ErrorResponse.FieldError> fieldErrors =
                List.of(new ErrorResponse.FieldError(e.getParameterName(), "필수 파라미터입니다."));
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                ErrorCode.VALIDATION_FAILED.getMessage(), fieldErrors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return respond(ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return respond(ErrorResponse.of(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return respond(ErrorResponse.of(ErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ErrorResponse> respond(ErrorResponse body) {
        return ResponseEntity.status(body.status()).body(body);
    }
}
