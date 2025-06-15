package org.example.bidflow.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionAdvisor {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RsData<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.warn("[유효성 검증 실패] 요청 파라미터 검증 오류 발생: {}", e.getMessage());
        
        String message = e.getBindingResult().getFieldErrors()
                .stream()
                .map(fe -> {
                    String fieldError = fe.getField() + ": " + fe.getDefaultMessage();
                    log.debug("[유효성 검증 상세] 필드 오류 - {}", fieldError);
                    return fe.getDefaultMessage();
                })
                .sorted()
                .collect(Collectors.joining("\n"));

        log.info("[유효성 검증 응답] 클라이언트에게 400 응답 전송: {}", message);
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(
                        new RsData<>(
                                "400-1",
                                message
                        )
                );
    }

    @ResponseStatus
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<RsData<Void>> ServiceExceptionHandle(ServiceException ex) {
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("[잘못된 인자] IllegalArgumentException 발생: {}", ex.getMessage(), ex);
        
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_ARGUMENT");
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        log.error("[잘못된 상태] IllegalStateException 발생: {}", ex.getMessage(), ex);
        
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "INVALID_STATE");
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("[예상치 못한 오류] 시스템 오류 발생: {}", ex.getMessage(), ex);
        
        Map<String, String> response = new HashMap<>();
        response.put("error", "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("errorType", "SYSTEM_ERROR");
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
