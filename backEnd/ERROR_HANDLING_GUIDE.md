# 에러 처리 시스템 개선 가이드

## 개요
기존의 단일 "시스템 오류가 발생했습니다" 메시지 대신, 상황별로 구체적이고 사용자 친화적인 에러 메시지를 제공하도록 시스템을 개선했습니다.

## 주요 개선사항

### 1. 예외 타입별 세분화 처리
- **JWT 관련 예외**: 토큰 만료, 잘못된 형식, 서명 오류 등
- **데이터베이스 예외**: 연결 오류, 제약조건 위반, 타임아웃 등
- **Redis 예외**: 연결 실패, 캐시 오류 등
- **HTTP 요청 예외**: 잘못된 형식, 누락된 파라미터, 지원되지 않는 메서드 등
- **유효성 검증 예외**: 필드별 상세 오류 정보 제공

### 2. 응답 형식 통일
```json
{
    "error": "사용자 친화적 에러 메시지",
    "errorType": "ERROR_CATEGORY",
    "timestamp": "1749958573944",
    "fieldErrors": {
        "fieldName": "필드별 오류 메시지"
    }
}
```

## 에러 타입별 응답 예시

### JWT 관련 오류
```json
// 토큰 만료
{
    "error": "토큰이 만료되었습니다. 다시 로그인해 주세요.",
    "errorType": "EXPIRED_TOKEN",
    "timestamp": "1749958573944"
}

// 잘못된 토큰 형식
{
    "error": "잘못된 토큰 형식입니다.",
    "errorType": "MALFORMED_TOKEN",
    "timestamp": "1749958573944"
}
```

### 유효성 검증 오류
```json
{
    "error": "이메일은 필수 입력 항목입니다.",
    "errorType": "VALIDATION_ERROR",
    "fieldErrors": {
        "email": "이메일은 필수 입력 항목입니다.",
        "password": "비밀번호는 최소 8자 이상이어야 합니다."
    },
    "timestamp": "1749958573944"
}
```

### 데이터베이스 오류
```json
// 중복 데이터
{
    "error": "이미 존재하는 데이터입니다. 중복된 값을 입력할 수 없습니다.",
    "errorType": "DATA_INTEGRITY_ERROR",
    "timestamp": "1749958573944"
}

// 연결 오류
{
    "error": "데이터베이스 연결에 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.",
    "errorType": "DATABASE_CONNECTION_ERROR",
    "timestamp": "1749958573944"
}
```

### Redis 연결 오류
```json
{
    "error": "캐시 서버 연결에 실패했습니다. 잠시 후 다시 시도해 주세요.",
    "errorType": "REDIS_CONNECTION_ERROR",
    "timestamp": "1749958573944"
}
```

### HTTP 요청 오류
```json
// 잘못된 JSON 형식
{
    "error": "JSON 형식이 올바르지 않습니다.",
    "errorType": "INVALID_REQUEST_FORMAT",
    "timestamp": "1749958573944"
}

// 지원되지 않는 HTTP 메서드
{
    "error": "지원되지 않는 HTTP 메서드입니다. 허용된 메서드: GET, POST",
    "errorType": "METHOD_NOT_ALLOWED",
    "timestamp": "1749958573944"
}
```

### 비즈니스 로직 오류
```json
// 경매 목록 없음
{
    "error": "등록된 경매가 없습니다. 새로운 경매가 등록될 때까지 기다려주세요.",
    "errorType": "BUSINESS_ERROR",
    "timestamp": "1749958573944"
}

// 사용자 없음
{
    "error": "해당 사용자를 찾을 수 없습니다. 사용자 ID를 다시 확인해주세요.",
    "errorType": "BUSINESS_ERROR",
    "timestamp": "1749958573944"
}
```

## 개발자를 위한 디버깅 정보

### 로그 레벨별 정보
- **ERROR**: 심각한 오류, 스택 트레이스 포함
- **WARN**: 예상 가능한 오류, 사용자 입력 문제 등
- **INFO**: 비즈니스 로직 성공/실패
- **DEBUG**: 상세한 실행 정보

### 예외 클래스 정보
시스템 오류의 경우 `exceptionClass` 필드에 실제 예외 클래스명을 포함하여 디버깅을 지원합니다.

```json
{
    "error": "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
    "errorType": "SYSTEM_ERROR",
    "exceptionClass": "NullPointerException",
    "timestamp": "1749958573944"
}
```

## 사용 권장사항

### 클라이언트 개발자용
1. `errorType`을 기반으로 에러 카테고리별 처리 구현
2. `fieldErrors`를 활용하여 폼 필드별 에러 표시
3. 특정 에러 타입에 대한 자동 재시도 로직 구현 가능

### 서버 개발자용
1. 새로운 비즈니스 예외는 `ServiceException` 사용
2. 구체적이고 사용자 친화적인 메시지 작성
3. 민감한 정보(스택 트레이스, 내부 구조 등)는 로그에만 기록

## 테스트 시나리오

### Postman 테스트 예시
1. **잘못된 토큰으로 API 호출**
   - 401 Unauthorized + 구체적인 토큰 오류 메시지

2. **필수 필드 누락한 회원가입 요청**
   - 400 Bad Request + 필드별 상세 오류 정보

3. **존재하지 않는 사용자 조회**
   - 404 Not Found + 사용자 친화적 메시지

4. **잘못된 JSON 형식 전송**
   - 400 Bad Request + JSON 형식 오류 메시지

이제 각 상황별로 정확한 원인을 파악할 수 있어 디버깅과 사용자 경험이 크게 개선됩니다. 