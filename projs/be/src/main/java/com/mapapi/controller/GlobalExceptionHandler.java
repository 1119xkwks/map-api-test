package com.mapapi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 글로벌 예외 처리 핸들러.
 * - 400: 파라미터 오류 (누락, 타입 불일치, 유효성 검증 실패)
 * - 502: 외부 API 호출 오류
 * - 500: 내부 서버 오류
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 필수 파라미터 누락
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("파라미터 누락: {}", ex.getMessage());
        return Map.of(
                "success", false,
                "error", "INVALID_PARAMS",
                "message", "sw_lat, sw_lng, ne_lat, ne_lng 파라미터가 필요합니다."
        );
    }

    /**
     * 파라미터 타입 불일치 (예: double에 문자열 전달)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("파라미터 타입 오류: {}", ex.getMessage());
        return Map.of(
                "success", false,
                "error", "INVALID_PARAMS",
                "message", "파라미터 '" + ex.getName() + "'의 값이 유효하지 않습니다."
        );
    }

    /**
     * 유효성 검증 실패 (IllegalArgumentException)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("유효성 검증 실패: {}", ex.getMessage());
        return Map.of(
                "success", false,
                "error", "INVALID_PARAMS",
                "message", ex.getMessage()
        );
    }

    /**
     * 외부 API 호출 실패 (RuntimeException 중 공공API/외부API 관련)
     * 메시지에 "공공데이터 API" 또는 "Kakao" 등 외부 API 관련 키워드가 포함된 경우 502를 반환한다.
     */
    @ExceptionHandler(RuntimeException.class)
    public Map<String, Object> handleRuntimeException(RuntimeException ex,
            org.springframework.web.context.request.WebRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String message = ex.getMessage();
        if (message != null && (message.contains("공공데이터 API") || message.contains("Kakao")
                || message.contains("공공API"))) {
            log.error("외부 API 오류: {}", ex.getMessage(), ex);
            response.setStatus(HttpStatus.BAD_GATEWAY.value());
            return Map.of(
                    "success", false,
                    "error", "EXTERNAL_API_ERROR",
                    "message", "공공데이터 API 호출에 실패했습니다. 잠시 후 다시 시도해주세요."
            );
        }
        // 외부 API 관련이 아닌 RuntimeException은 내부 오류로 처리
        log.error("내부 서버 오류 (RuntimeException): {}", ex.getMessage(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Map.of(
                "success", false,
                "error", "INTERNAL_ERROR",
                "message", "서버 오류가 발생했습니다."
        );
    }

    /**
     * 기타 내부 오류
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception ex) {
        log.error("내부 서버 오류: {}", ex.getMessage(), ex);
        return Map.of(
                "success", false,
                "error", "INTERNAL_ERROR",
                "message", "서버 오류가 발생했습니다."
        );
    }
}
