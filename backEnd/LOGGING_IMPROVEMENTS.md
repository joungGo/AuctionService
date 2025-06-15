# 🔍 BidFlow 로깅 및 에러 처리 개선사항

## 📋 개선사항 요약

프로젝트의 전체 코드를 분석하여 에러 로그를 검증하고 개선했습니다. 개발자가 디버깅하기 편하도록 다음과 같은 변경사항을 적용했습니다.

---

## 🛠️ 주요 개선사항

### 1. **전역 예외 처리 강화** (`GlobalExceptionAdvisor`)
- **추가된 기능:**
  - 모든 예외에 대한 상세 로깅 추가
  - 에러 응답에 타임스탬프와 에러 타입 정보 포함
  - 예상치 못한 에러에 대한 일반적인 Exception Handler 추가
  - 한국어 로그 메시지로 디버깅 편의성 향상

```java
// 개선 전
@ExceptionHandler(ServiceException.class)
public ResponseEntity<RsData<Void>> ServiceExceptionHandle(ServiceException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(/*...*/)
}

// 개선 후  
@ExceptionHandler(ServiceException.class)
public ResponseEntity<RsData<Void>> ServiceExceptionHandle(ServiceException ex) {
    log.error("[서비스 예외] 비즈니스 로직 오류 발생 - 코드: {}, 메시지: {}, 상태코드: {}", 
            ex.getCode(), ex.getMsg(), ex.getStatusCode(), ex);
    return ResponseEntity.status(ex.getStatusCode()).body(/*...*/)
}
```

### 2. **사용자 서비스 로깅 개선** (`UserService`)
- **추가된 기능:**
  - 회원가입/로그인 과정의 단계별 로깅
  - 보안 관련 이벤트 추적 (실패한 로그인 등)
  - 사용자 정보 변경 내역 추적
  - 민감한 정보 제외한 안전한 로깅

```java
// 예시: 로그인 과정
log.info("[로그인] 로그인 시도 - 이메일: {}", request.getEmail());
// ... 로직 수행 ...
log.info("[로그인 성공] JWT 토큰 발급 완료 - userUUID: {}, 닉네임: {}", user.getUserUUID(), user.getNickname());
```

### 3. **입찰 서비스 성능 및 비즈니스 로깅** (`BidService`)
- **추가된 기능:**
  - 입찰 처리 시간 측정 및 로깅
  - 입찰 검증 단계별 상세 로깅
  - Redis 최고가 갱신 과정 추적
  - 에러 발생 시 컨텍스트 정보 포함

```java
// 예시: 입찰 처리 성능 로깅
long startTime = System.currentTimeMillis();
// ... 입찰 로직 수행 ...
long endTime = System.currentTimeMillis();
log.info("[입찰 성공] 입찰 처리 완료 - 경매ID: {}, 입찰자: {}, 금액: {}, 처리시간: {}ms", 
        auctionId, user.getNickname(), request.getAmount(), (endTime - startTime));
```

### 4. **스케줄러 로깅 강화** (`AuctionSchedulerEvent`)
- **추가된 기능:**
  - 스케줄러 실행 주기와 성능 모니터링
  - 경매 종료 처리 상세 로깅
  - 개별 경매 처리 오류 격리 및 로깅
  - 배치 처리 결과 요약 로깅

```java
log.info("[Scheduler] 경매 상태 검사 완료 - 검사 대상: {}, 종료 처리: {}, 처리 시간: {}ms", 
        processedCount, finishedCount, (endTime - startTime));
```

### 5. **Redis 작업 로깅 추가** (`RedisCommon`)
- **추가된 기능:**
  - Redis 읽기/쓰기 작업 추적
  - 데이터 직렬화/역직렬화 오류 처리
  - 캐시 미스/히트 상황 로깅
  - Redis 연결 오류 상세 정보

### 6. **로그 설정 최적화** (`application.yml`, `application-dev.yml`)
- **추가된 기능:**
  - 패키지별 세밀한 로그 레벨 설정
  - 개발/운영 환경별 로그 설정 분리
  - 로그 파일 롤링 및 보관 정책
  - 컬러 출력과 타임스탬프 포맷팅

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %highlight(%-5level) %cyan(%logger{36}) - %msg%n"
  level:
    org.example.bidflow: DEBUG
    org.example.bidflow.domain.bid.service: INFO
    org.example.bidflow.global.exception: INFO
```

---

## 🎯 새로 추가된 유틸리티 클래스

### 1. **LoggingUtil** - 표준화된 로깅 유틸리티
- **주요 기능:**
  - 메서드 시작/종료 로깅
  - 비즈니스 단계별 로깅
  - 성능 경고 로깅
  - 사용자 액션 추적
  - 외부 API 호출 로깅

```java
// 사용 예시
LoggingUtil.logMethodStart("UserService", "login", request.getEmail());
LoggingUtil.logBusinessStep("회원가입", "이메일 인증 확인", request.getEmail());
LoggingUtil.logPerformanceWarning("입찰처리", 1500L, 1000L);
```

### 2. **PerformanceLoggingAspect** - AOP 기반 성능 모니터링
- **주요 기능:**
  - 자동 성능 측정 (Service, Controller, Repository)
  - 임계치 기반 성능 경고
  - 메서드 실행 시간 추적
  - 에러 발생 시 컨텍스트 정보 수집

```java
// 자동으로 모든 서비스 메서드에 적용
@Around("execution(* org.example.bidflow.domain..service.*.*(..))")
public Object logServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
    // 성능 측정 및 로깅 로직
}
```

---

## 📊 로그 레벨별 사용 가이드

### **DEBUG** 레벨
- 개발 시 상세한 실행 흐름 추적
- 변수값, 파라미터 정보
- Redis 읽기/쓰기 상세 정보
- 메서드 진입/종료 로깅

### **INFO** 레벨  
- 중요한 비즈니스 이벤트
- 사용자 액션 (로그인, 회원가입, 입찰 등)
- 시스템 상태 변경
- 성능 메트릭

### **WARN** 레벨
- 성능 임계치 초과
- 비즈니스 규칙 위반 (중복 입찰 등)
- 외부 시스템 응답 지연

### **ERROR** 레벨
- 예외 발생 및 에러 처리
- 시스템 오류
- 외부 API 호출 실패

---

## 🔧 디버깅 가이드

### **일반적인 디버깅 시나리오**

1. **사용자 인증 문제**
   ```bash
   # 로그 검색 예시
   grep -r "로그인\|회원가입" logs/
   grep -r "userUUID.*{사용자ID}" logs/
   ```

2. **입찰 처리 오류**
   ```bash
   # 입찰 관련 로그 확인
   grep -r "입찰.*경매ID.*{경매번호}" logs/
   grep -r "성능 경고.*입찰" logs/
   ```

3. **스케줄러 문제**
   ```bash
   # 스케줄러 실행 로그 확인
   grep -r "Scheduler" logs/
   grep -r "경매 종료 처리" logs/
   ```

4. **Redis 연결 문제**
   ```bash
   # Redis 관련 오류 확인
   grep -r "Redis 오류" logs/
   grep -r "연결.*실패" logs/
   ```

### **로그 파일 위치**
- **개발환경**: `logs/bidflow-dev.log`
- **운영환경**: `logs/bidflow-application.log`

---

## 📈 성능 모니터링 

### **자동 성능 측정**
- 모든 Service 메서드 실행 시간 추적
- 1초 이상 소요 시 성능 주의 경고
- 2초 이상 소요 시 성능 경고 발생

### **성능 임계치**
```java
private static final long PERFORMANCE_THRESHOLD_MS = 1000; // 1초
private static final long SLOW_QUERY_THRESHOLD_MS = 2000;  // 2초
```

### **중요 비즈니스 메서드 모니터링**
- `create*`, `update*`, `delete*` 메서드
- `login`, `signup` 메서드  
- `bid*`, `auction*` 메서드
- `process*` 메서드

---

## 🚀 사용법

### **개발 환경에서 로그 확인**
```bash
# 실시간 로그 확인
tail -f logs/bidflow-dev.log

# 특정 패턴 로그 확인
tail -f logs/bidflow-dev.log | grep "ERROR\|WARN"

# 성능 관련 로그만 확인
tail -f logs/bidflow-dev.log | grep "성능\|처리시간"
```

### **특정 사용자 액션 추적**
```bash
# 특정 사용자의 모든 액션 추적
grep "userUUID.*{사용자UUID}" logs/bidflow-dev.log

# 특정 경매의 모든 이벤트 추적  
grep "경매ID.*{경매번호}" logs/bidflow-dev.log
```

---

## ⚠️ 주의사항

1. **민감한 정보 로깅 금지**
   - 비밀번호, 토큰 전체 내용 로깅 X
   - 개인정보는 마스킹 처리

2. **로그 레벨 적절히 설정**
   - 운영환경에서는 DEBUG 레벨 비활성화 권장
   - 성능에 영향을 주지 않도록 조절

3. **로그 파일 관리**
   - 정기적인 로그 파일 정리
   - 디스크 용량 모니터링

---

## 📞 문의사항

로깅 관련 문의나 개선사항이 있으면 개발팀으로 연락주세요.

**개선사항 적용일**: `2024년 현재`  
**담당자**: `개발팀`  
**문서 버전**: `v1.0` 