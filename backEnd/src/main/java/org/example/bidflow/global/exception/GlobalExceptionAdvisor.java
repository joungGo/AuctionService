package org.example.bidflow.global.exception;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.dto.RsData;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.sql.SQLTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvisor {

    // =========================== 비즈니스 로직 예외 ===========================
    
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> handleServiceException(ServiceException ex) {
        log.error("[서비스 예외] 비즈니스 로직 오류 발생 - 코드: {}, 메시지: {}, 상태코드: {}", 
                ex.getCode(), ex.getMsg(), ex.getStatusCode(), ex);
        
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(
                        new RsData<>(
                                ex.getCode(),
                                ex.getMsg()
                        )
                );
    }

    // =========================== 유효성 검증 예외 ===========================
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("[유효성 검증 실패] 요청 파라미터 검증 오류 발생: {}", e.getMessage());
        
        Map<String, String> fieldErrors = new HashMap<>();
        String firstErrorMessage = null;
        
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            String field = fieldError.getField();
            String message = fieldError.getDefaultMessage();
            fieldErrors.put(field, message);
            
            if (firstErrorMessage == null) {
                firstErrorMessage = message;
            }
            
            log.debug("[유효성 검증 상세] 필드 오류 - {}: {}", field, message);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("error", firstErrorMessage != null ? firstErrorMessage : "입력값 검증에 실패했습니다.");
        response.put("fieldErrors", fieldErrors);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "VALIDATION_ERROR");
        
        log.info("[유효성 검증 응답] 클라이언트에게 400 응답 전송: {}", firstErrorMessage);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("[제약조건 위반] 데이터 제약조건 검증 실패: {}", ex.getMessage());
        
        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "데이터 제약조건을 위반했습니다: " + message);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "CONSTRAINT_VIOLATION");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // =========================== JWT 관련 예외 ===========================
    
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Map<String, Object>> handleExpiredJwtException(ExpiredJwtException ex) {
        log.warn("[JWT 오류] 토큰 만료: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "토큰이 만료되었습니다. 다시 로그인해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "EXPIRED_TOKEN");
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(MalformedJwtException.class)
    public ResponseEntity<Map<String, Object>> handleMalformedJwtException(MalformedJwtException ex) {
        log.warn("[JWT 오류] 잘못된 토큰 형식: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "잘못된 토큰 형식입니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "MALFORMED_TOKEN");
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(UnsupportedJwtException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedJwtException(UnsupportedJwtException ex) {
        log.warn("[JWT 오류] 지원되지 않는 토큰: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "지원되지 않는 토큰입니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "UNSUPPORTED_TOKEN");
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleJwtSecurityException(SecurityException ex) {
        log.warn("[JWT 오류] 토큰 서명 검증 실패: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "토큰 서명이 유효하지 않습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_TOKEN_SIGNATURE");
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(JwtException ex) {
        log.warn("[JWT 오류] JWT 처리 실패: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "토큰 처리 중 오류가 발생했습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "JWT_ERROR");
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    // =========================== 데이터베이스 관련 예외 ===========================
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("[데이터베이스 오류] 데이터 무결성 제약조건 위반: {}", ex.getMessage(), ex);
        
        String message = "데이터 제약조건을 위반했습니다.";
        if (ex.getMessage().contains("Duplicate entry")) {
            message = "이미 존재하는 데이터입니다. 중복된 값을 입력할 수 없습니다.";
        } else if (ex.getMessage().contains("foreign key constraint")) {
            message = "관련된 데이터가 존재하지 않습니다.";
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "DATA_INTEGRITY_ERROR");
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(SQLTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleSQLTimeoutException(SQLTimeoutException ex) {
        log.error("[데이터베이스 오류] SQL 타임아웃: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "데이터베이스 응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "DATABASE_TIMEOUT");
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    @ExceptionHandler(SQLSyntaxErrorException.class)
    public ResponseEntity<Map<String, Object>> handleSQLSyntaxErrorException(SQLSyntaxErrorException ex) {
        log.error("[데이터베이스 오류] SQL 문법 오류: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "데이터베이스 처리 중 오류가 발생했습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "DATABASE_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<Map<String, Object>> handleSQLException(SQLException ex) {
        log.error("[데이터베이스 오류] SQL 예외 발생: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "데이터베이스 연결에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "DATABASE_CONNECTION_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(DataAccessException ex) {
        log.error("[데이터베이스 오류] 데이터 접근 오류: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "데이터 처리 중 오류가 발생했습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "DATA_ACCESS_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    // =========================== Redis 관련 예외 ===========================
    
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<Map<String, Object>> handleRedisConnectionFailureException(RedisConnectionFailureException ex) {
        log.error("[Redis 오류] Redis 연결 실패: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "캐시 서버 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "REDIS_CONNECTION_ERROR");
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    // =========================== HTTP 요청 관련 예외 ===========================
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("[HTTP 오류] 요청 본문 읽기 실패: {}", ex.getMessage());
        
        String message = "요청 형식이 올바르지 않습니다.";
        if (ex.getMessage().contains("JSON")) {
            message = "JSON 형식이 올바르지 않습니다.";
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_REQUEST_FORMAT");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.warn("[HTTP 오류] 지원되지 않는 HTTP 메서드: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "지원되지 않는 HTTP 메서드입니다. 허용된 메서드: " + String.join(", ", ex.getSupportedMethods()));
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "METHOD_NOT_ALLOWED");
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        log.warn("[HTTP 오류] 지원되지 않는 미디어 타입: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "지원되지 않는 Content-Type입니다. application/json을 사용해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "UNSUPPORTED_MEDIA_TYPE");
        
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(response);
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        log.warn("[HTTP 오류] 필수 요청 파라미터 누락: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "필수 파라미터가 누락되었습니다: " + ex.getParameterName());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "MISSING_PARAMETER");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        log.warn("[HTTP 오류] 필수 헤더 누락: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "필수 헤더가 누락되었습니다: " + ex.getHeaderName());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "MISSING_HEADER");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        log.warn("[HTTP 오류] 파라미터 타입 불일치: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "파라미터 형식이 올바르지 않습니다: " + ex.getName());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "PARAMETER_TYPE_MISMATCH");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // =========================== 기타 예외 ===========================
    
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        log.warn("[응답 상태 예외] HTTP 상태 예외: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getReason() != null ? ex.getReason() : "요청을 처리할 수 없습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "HTTP_STATUS_ERROR");
        
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }
    
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, Object>> handleMailException(MailException ex) {
        log.error("[메일 오류] 이메일 전송 실패: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "이메일 전송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "EMAIL_SEND_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("[파일 업로드 오류] 파일 크기 초과: {}", ex.getMessage());
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", "업로드할 수 있는 파일 크기를 초과했습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "FILE_SIZE_EXCEEDED");
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("[잘못된 인자] IllegalArgumentException 발생: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage() != null ? ex.getMessage() : "잘못된 요청 파라미터입니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_ARGUMENT");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.error("[잘못된 상태] IllegalStateException 발생: {}", ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage() != null ? ex.getMessage() : "현재 상태에서는 해당 작업을 수행할 수 없습니다.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_STATE");
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // =========================== 최종 예외 핸들러 ===========================
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneralException(Exception ex) {
        log.error("[예상치 못한 오류] 시스템 오류 발생 - 클래스: {}, 메시지: {}", 
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
        
        Map<String, Object> response = new HashMap<>();
        
        // 개발 환경에서는 더 자세한 정보 제공
        String errorMessage = "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        
        // 특정 예외들에 대한 추가 처리
        if (ex.getCause() != null) {
            String cause = ex.getCause().getClass().getSimpleName();
            if (cause.contains("Connection")) {
                errorMessage = "서버 연결에 문제가 발생했습니다. 잠시 후 다시 시도해주세요.";
            } else if (cause.contains("Timeout")) {
                errorMessage = "요청 처리 시간이 초과되었습니다. 잠시 후 다시 시도해주세요.";
            }
        }
        
        response.put("error", errorMessage);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "SYSTEM_ERROR");
        response.put("exceptionClass", ex.getClass().getSimpleName());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
