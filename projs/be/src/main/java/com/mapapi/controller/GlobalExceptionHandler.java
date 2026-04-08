package com.mapapi.controller;

import com.mapapi.util.XmlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * 글로벌 예외 처리 핸들러.
 * - 400: 파라미터 오류
 * - 502: 외부 API 호출/응답 오류
 * - 422: XML 파싱 오류 (API 성공했으나 데이터 파싱 실패)
 * - 500: 내부 서버 오류
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 필수 파라미터 누락 → 400 */
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

    /** 파라미터 타입 불일치 → 400 */
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

    /** 유효성 검증 실패 → 400 */
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

    /** 외부 API 네트워크/HTTP 오류 → 502 */
    @ExceptionHandler(RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleRestClientException(RestClientException ex) {
        log.error("[외부 API 호출 실패] 네트워크/HTTP 오류: {}", ex.getMessage());
        return Map.of(
                "success", false,
                "error", "EXTERNAL_API_NETWORK_ERROR",
                "message", "외부 API 호출에 실패했습니다 (네트워크 오류). 잠시 후 다시 시도해주세요."
        );
    }

    /** 공공API 에러 응답코드 → 502 */
    @ExceptionHandler(XmlParserUtil.ApiResponseException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleApiResponseException(XmlParserUtil.ApiResponseException ex) {
        log.error("[공공API 에러 응답] code={}, msg={}", ex.getResultCode(), ex.getResultMsg());
        return Map.of(
                "success", false,
                "error", "EXTERNAL_API_RESPONSE_ERROR",
                "message", "공공API 에러 응답 [" + ex.getResultCode() + "]: " + ex.getResultMsg()
        );
    }

    /** XML 파싱 실패 (API 호출은 성공) → 422 */
    @ExceptionHandler(XmlParserUtil.XmlParseException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, Object> handleXmlParseException(XmlParserUtil.XmlParseException ex) {
        log.error("[XML 파싱 실패] API 응답 수신 성공, 파싱 실패: {}", ex.getMessage());
        return Map.of(
                "success", false,
                "error", "XML_PARSE_ERROR",
                "message", "API 응답 데이터 파싱에 실패했습니다: " + ex.getMessage()
        );
    }

    /** 기타 RuntimeException → 500 */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleRuntimeException(RuntimeException ex) {
        log.error("[내부 오류] {}", ex.getMessage(), ex);
        return Map.of(
                "success", false,
                "error", "INTERNAL_ERROR",
                "message", "서버 오류가 발생했습니다: " + ex.getMessage()
        );
    }

    /** 기타 Exception → 500 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception ex) {
        log.error("[내부 오류] {}", ex.getMessage(), ex);
        return Map.of(
                "success", false,
                "error", "INTERNAL_ERROR",
                "message", "서버 오류가 발생했습니다."
        );
    }
}
