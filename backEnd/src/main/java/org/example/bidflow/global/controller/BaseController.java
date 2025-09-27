package org.example.bidflow.global.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.constants.BusinessConstants;
import org.example.bidflow.global.constants.ErrorCode;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;

/**
 * 모든 컨트롤러의 공통 기능을 제공하는 추상 클래스
 * 
 * 이 클래스는 REST API 컨트롤러들의 공통 기능을 추상화하여 제공합니다.
 * 응답 처리, 예외 처리, 로깅 등의 공통 로직을 통합하여 일관된 API 응답 형식과 에러 처리를 보장합니다.
 * 
 * 주요 기능:
 * - 표준화된 HTTP 응답 생성 (200, 201, 400, 404, 500)
 * - RsData 래퍼 클래스를 통한 일관된 응답 형식
 * - 메서드 실행 시간 측정 및 로깅
 * - 에러 로깅 및 디버깅 정보 제공
 * 
 * 사용 방법:
 * @RestController
 * @RequestMapping("/api/example")
 * public class ExampleController extends BaseController {
 *     
 *     @GetMapping("/data")
 *     public ResponseEntity<RsData<DataDto>> getData() {
 *         long startTime = startOperation("getData", "데이터 조회");
 *         try {
 *             DataDto data = service.getData();
 *             endOperation("getData", "데이터 조회", startTime);
 *             return successResponse("데이터 조회 완료", data);
 *         } catch (Exception e) {
 *             endOperation("getData", "데이터 조회", startTime);
 *             throw e;
 *         }
 *     }
 * }
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 * @see RsData
 * @see ErrorCode
 * @see BusinessConstants
 */
@Slf4j
public abstract class BaseController {

    /**
     * 성공 응답을 생성합니다.
     * @param message 응답 메시지
     * @param data 응답 데이터
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> successResponse(String message, T data) {
        RsData<T> rsData = new RsData<>(ErrorCode.HTTP_OK, message, data);
        log.debug("[응답 생성] 성공 응답 - 메시지: {}, 데이터 타입: {}", message, 
                data != null ? data.getClass().getSimpleName() : "null");
        return ResponseEntity.ok(rsData);
    }

    /**
     * 성공 응답을 생성합니다. (데이터 없음)
     * @param message 응답 메시지
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> successResponse(String message) {
        RsData<T> rsData = new RsData<>(ErrorCode.HTTP_OK, message, null);
        log.debug("[응답 생성] 성공 응답 - 메시지: {}", message);
        return ResponseEntity.ok(rsData);
    }

    /**
     * 생성 성공 응답을 생성합니다.
     * @param message 응답 메시지
     * @param data 응답 데이터
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> createdResponse(String message, T data) {
        RsData<T> rsData = new RsData<>(ErrorCode.HTTP_CREATED, message, data);
        log.debug("[응답 생성] 생성 성공 응답 - 메시지: {}, 데이터 타입: {}", message, 
                data != null ? data.getClass().getSimpleName() : "null");
        return ResponseEntity.status(BusinessConstants.HTTP_STATUS_CREATED).body(rsData);
    }

    /**
     * 에러 응답을 생성합니다.
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> errorResponse(String errorCode, String errorMessage) {
        RsData<T> rsData = new RsData<>(errorCode, errorMessage, null);
        log.warn("[응답 생성] 에러 응답 - 코드: {}, 메시지: {}", errorCode, errorMessage);
        return ResponseEntity.badRequest().body(rsData);
    }

    /**
     * 404 Not Found 응답을 생성합니다.
     * @param message 에러 메시지
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> notFoundResponse(String message) {
        RsData<T> rsData = new RsData<>(ErrorCode.HTTP_NOT_FOUND, message, null);
        log.warn("[응답 생성] 404 응답 - 메시지: {}", message);
        return ResponseEntity.status(BusinessConstants.HTTP_STATUS_NOT_FOUND).body(rsData);
    }

    /**
     * 500 Internal Server Error 응답을 생성합니다.
     * @param message 에러 메시지
     * @return ResponseEntity<RsData<T>>
     */
    protected <T> ResponseEntity<RsData<T>> serverErrorResponse(String message) {
        RsData<T> rsData = new RsData<>(ErrorCode.HTTP_INTERNAL_SERVER_ERROR, message, null);
        log.error("[응답 생성] 500 응답 - 메시지: {}", message);
        return ResponseEntity.status(BusinessConstants.HTTP_STATUS_INTERNAL_SERVER_ERROR).body(rsData);
    }

    /**
     * 메서드 실행 시간을 측정하고 로그를 남깁니다.
     * @param methodName 메서드명
     * @param operation 작업 내용
     * @return 실행 시작 시간
     */
    protected long startOperation(String methodName, String operation) {
        long startTime = System.currentTimeMillis();
        log.info("[{}] {} 시작", methodName, operation);
        return startTime;
    }

    /**
     * 메서드 실행 완료 로그를 남깁니다.
     * @param methodName 메서드명
     * @param operation 작업 내용
     * @param startTime 시작 시간
     */
    protected void endOperation(String methodName, String operation, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        log.info("[{}] {} 완료 - 실행시간: {}ms", methodName, operation, executionTime);
    }
}
