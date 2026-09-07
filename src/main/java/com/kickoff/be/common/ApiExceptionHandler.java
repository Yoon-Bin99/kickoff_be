package com.kickoff.be.common;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 쿼리 스트링에 무효 UTF-8 바이트가 들어온 경우 (계약서 §0, v1.24.2).
     *
     * <b>왜 Tomcat 클래스를 직접 잡는가.</b> 파라미터를 디코딩하다 실패하면 컨테이너가
     * 자기 예외를 던지는데, 그게 이 클래스다({@code MalformedInputException} 을 감싸고
     * 있다). 스프링이 감싸주는 공용 타입이 없어서 여기까지 그대로 올라오고, 잡는
     * 핸들러가 없으면 아래 {@code handleUnexpected} 로 떨어져 <b>500</b> 이 나간다.
     * 클라이언트가 잘못 보낸 요청이므로 400 이 맞다.
     *
     * 상위 타입인 {@code IllegalStateException} 을 잡으면 우리 코드의 진짜 버그까지
     * 400 으로 감춰지므로 좁게 잡는다. 대신 서블릿 컨테이너를 Jetty·Undertow 로 바꾸면
     * <b>이 핸들러는 죽은 코드가 된다</b> — 그때는 그 컨테이너가 던지는 타입으로 바꿔야 한다.
     *
     * Tomcat 의 {@code getErrorCode()} 는 쓰지 않는다. 컨테이너 내부 코드를 우리 응답에
     * 실을 이유가 없다.
     */
    @ExceptionHandler(InvalidParameterException.class)
    public ResponseEntity<ErrorResponse> handleMalformedParameter(InvalidParameterException e) {
        return respond(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, "요청을 해석할 수 없습니다."));
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

    /**
     * 매핑되지 않은 경로. 아래 catch-all 이 먼저 잡아 500 으로 바꿔버리면
     * FE 가 경로 오타를 서버 장애로 오인하므로 따로 받아 404 로 내린다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return respond(ErrorResponse.of(ErrorCode.NOT_FOUND));
    }

    /**
     * 경로는 맞는데 메서드가 틀린 경우 (계약서 §0, v1.12.1).
     *
     * 바로 위 경로 오타 처리와 같은 이유로 따로 받는다. 일반 500 으로 떨어지면 클라이언트가
     * 자기 실수를 서버 장애로 읽는다 — 실제로 FE 가 accept 를 PATCH 로 부르고 INTERNAL_ERROR
     * 를 받아 "배포 직후 매칭이 깨졌다"는 잘못된 가설을 쫓은 일이 있었다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e) {
        return respond(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED));
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
