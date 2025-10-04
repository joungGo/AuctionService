# Redis 리스너 강화 기능 테스트 가이드

## 📋 테스트 개요

이 가이드는 다음 구현 사항들을 테스트합니다:
1. ✅ 구조화된 로깅 시스템
2. ✅ Redis 연결 상태 모니터링
3. ✅ 에러 처리 및 컨텍스트 정보 수집
4. ✅ Redis 리스너 에러 핸들러

---

## 🚀 테스트 환경 설정

### 1. 백엔드 서버 시작

```bash
cd C:\Users\jounghyeon\AuctionService\backEnd
./gradlew bootRun
```

### 2. 로그 파일 모니터링

별도 터미널에서:
```bash
cd C:\Users\jounghyeon\AuctionService\backEnd\logs
tail -f bidflow-application.log
```

### 3. 프론트엔드 서버 시작

```bash
cd C:\Users\jounghyeon\AuctionService_FE
npm run dev
```

---

## 🧪 테스트 시나리오

### Test 1: Redis 연결 상태 모니터링

**목적**: RedisHealthChecker가 정상적으로 연결 상태를 체크하는지 확인

#### 예상 로그 (10초 후 첫 체크)

```
✅ [Redis Health] 연결 상태 정상: {
  "event": "redis_connection_check",
  "status": "success",
  "responseTimeMs": 3,
  "timestamp": 1759415601234
}
```

#### 검증 포인트
- ✅ 서버 시작 후 10초 후 첫 로그 출력
- ✅ 이후 30초마다 체크 (연결 정상 시 로그 출력 안함)
- ✅ `responseTimeMs`가 합리적인 범위 (0~100ms)

---

### Test 2: 구조화된 이벤트 로깅 (정상 케이스)

**목적**: 경매 이벤트 발행 시 구조화된 로그 출력 확인

#### 테스트 방법

1. 브라우저에서 `http://localhost:3000/admin` 접속 (관리자 로그인)
2. "새 경매 등록" 버튼 클릭
3. 경매 정보 입력 후 등록

#### 예상 로그

**1) Redis 메시지 수신**
```
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "main:new-auctions",
  "payloadSize": 250,
  "timestamp": 1759415601234
}
```

**2) 메시지 처리 성공**
```
📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "main:new-auctions",
  "eventType": "NEW_AUCTION",
  "auctionId": 1,
  "stompTopic": "/sub/main/new-auctions",
  "processingTimeMs": 5,
  "timestamp": 1759415601235
}
```

#### 검증 포인트
- ✅ 모든 필드가 JSON 형태로 예쁘게 포맷팅됨
- ✅ `processingTimeMs`가 합리적인 범위 (0~50ms)
- ✅ 채널과 토픽이 올바르게 매핑됨

---

### Test 3: 입찰 이벤트 실시간 로깅

**목적**: 입찰 시 BID_UPDATE 이벤트 로깅 확인

#### 테스트 방법

1. 브라우저에서 `http://localhost:3000/auctions/1/bid` 접속
2. 입찰 금액 입력 후 "입찰하기" 클릭

#### 예상 로그

```
📢 [Redis] 입찰 업데이트 알림 발행: auctionId=1, currentBid=50000, bidder=사용자1

📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "auction:1",
  "payloadSize": 301,
  "timestamp": 1759415601468
}

📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "auction:1",
  "eventType": "BID_UPDATE",
  "auctionId": 1,
  "stompTopic": "/sub/auction/1",
  "processingTimeMs": 2,
  "timestamp": 1759415601469
}
```

#### 검증 포인트
- ✅ 입찰 즉시 로그 출력
- ✅ 3개의 로그가 순차적으로 출력 (발행 → 수신 → 처리)
- ✅ `auctionId`와 `currentBid` 정보 정확

---

### Test 4: Redis 메시지 파싱 실패 (에러 케이스)

**목적**: 잘못된 JSON 메시지 수신 시 에러 처리 확인

#### 테스트 방법

Redis CLI를 통해 잘못된 JSON 발행:

```bash
# Redis CLI 접속
redis-cli

# 잘못된 JSON 발행
PUBLISH main:new-auctions "{ invalid json }"
```

#### 예상 로그

```
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "main:new-auctions",
  "payloadSize": 17,
  "timestamp": 1759415601234
}

⚠️ [Redis] 메시지 파싱 실패: {
  "event": "redis_message_parsing_failed",
  "status": "failure",
  "channel": "main:new-auctions",
  "payloadLength": 17,
  "error": "Unexpected character 'i' at position 2",
  "timestamp": 1759415601235
}
```

#### 검증 포인트
- ✅ 파싱 실패 로그 출력
- ✅ 에러 메시지 상세 정보 포함
- ✅ 서버가 다운되지 않고 계속 작동

---

### Test 5: Redis 연결 끊김 시뮬레이션

**목적**: Redis 연결 장애 시 모니터링 로그 확인

#### 테스트 방법

**1) Redis 서버 중지**
```bash
# Windows (관리자 권한 PowerShell)
net stop Redis

# 또는 Docker 사용 시
docker stop redis
```

**2) 로그 확인 (30초 대기)**

#### 예상 로그

```
❌ [Redis Health] 연결 실패: {
  "event": "redis_connection_check",
  "status": "failure",
  "error": "Connection refused",
  "consecutiveFailures": 1,
  "timestamp": 1759415601234
}

❌ [Redis Health] 연결 실패: {
  "event": "redis_connection_check",
  "status": "failure",
  "error": "Connection refused",
  "consecutiveFailures": 2,
  "timestamp": 1759415631234
}

❌ [Redis Health] 연결 실패: {
  "event": "redis_connection_check",
  "status": "failure",
  "error": "Connection refused",
  "consecutiveFailures": 3,
  "timestamp": 1759415661234
}

⚠️ [Redis Health] 연속 3회 연결 실패! Redis 서버 상태를 확인하세요.
```

**3) Redis 서버 재시작**
```bash
# Windows
net start Redis

# Docker
docker start redis
```

**4) 연결 복구 로그 확인 (30초 대기)**

#### 예상 로그

```
🔄 [Redis Health] 연결 복구됨: {
  "event": "redis_connection_restored",
  "status": "success",
  "downTimeMs": 90000,
  "timestamp": 1759415691234
}

✅ [Redis Health] 연결 상태 정상: {
  "event": "redis_connection_check",
  "status": "success",
  "responseTimeMs": 5,
  "timestamp": 1759415691235
}
```

#### 검증 포인트
- ✅ 연속 실패 횟수 추적 (`consecutiveFailures`)
- ✅ 3회 실패 시 경고 로그 추가 출력
- ✅ 복구 시 다운타임 계산 (`downTimeMs`)
- ✅ 복구 후 정상 연결 로그 출력

---

### Test 6: STOMP 전송 실패 (에러 케이스)

**목적**: STOMP 메시지 전송 실패 시 에러 로깅 확인

#### 테스트 방법

이 테스트는 자연스럽게 발생하는 경우가 드물므로, Redis CLI로 존재하지 않는 채널에 메시지 발행:

```bash
redis-cli
PUBLISH unknown:channel '{"eventType":"TEST","auctionId":999}'
```

#### 예상 로그

```
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "unknown:channel",
  "payloadSize": 45,
  "timestamp": 1759415601234
}

⚠️ [Redis] 알 수 없는 채널: {
  "event": "unknown_redis_channel",
  "status": "warning",
  "channel": "unknown:channel",
  "timestamp": 1759415601235
}
```

#### 검증 포인트
- ✅ 알 수 없는 채널 경고 로그 출력
- ✅ 서버가 다운되지 않고 계속 작동

---

### Test 7: 리스너 전체 에러 처리 (강화된 에러 로깅)

**목적**: 예외 발생 시 상세한 컨텍스트 정보 수집 확인

#### 테스트 방법

Redis CLI로 잘못된 이벤트 타입 발행:

```bash
redis-cli
PUBLISH main:new-auctions '{"eventType":"INVALID_TYPE","auctionId":1}'
```

#### 예상 로그

```
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "main:new-auctions",
  "payloadSize": 45,
  "timestamp": 1759415601234
}

⚠️ [Redis] 메시지 검증 실패: {
  "event": "redis_message_validation_failed",
  "status": "failure",
  "eventType": "INVALID_TYPE",
  "auctionId": 1,
  "reason": "알 수 없는 이벤트 타입",
  "timestamp": 1759415601235
}
```

**또는 리스너에서 예외 발생 시:**

```
❌ [Redis Listener] 메시지 처리 실패: {
  "event": "redis_listener_error",
  "status": "failure",
  "channel": "main:new-auctions",
  "payloadLength": 45,
  "payloadPreview": "{\"eventType\":\"INVALID_TYPE\",\"auctionId\":1}",
  "errorType": "VALIDATION_ERROR",
  "errorMessage": "Invalid event type",
  "stackTracePreview": "java.lang.IllegalArgumentException: Invalid event type\n\tat org.example.bidflow...",
  "timestamp": 1759415601235
}
```

#### 검증 포인트
- ✅ 에러 타입 자동 분류 (`errorType`)
- ✅ 원본 페이로드 보존 (`payloadPreview`)
- ✅ 스택 트레이스 포함 (`stackTracePreview`)
- ✅ 모든 필드가 JSON 형태로 출력

---

## 📊 테스트 체크리스트

### Redis 연결 상태 모니터링
- [ ] 서버 시작 후 10초 후 첫 체크 로그 출력
- [ ] 30초마다 체크 (정상 시 로그 출력 안함)
- [ ] Redis 중지 시 연속 실패 횟수 추적
- [ ] 3회 연속 실패 시 경고 로그
- [ ] Redis 재시작 시 연결 복구 로그 및 다운타임 계산

### 구조화된 로깅
- [ ] 모든 로그가 JSON 형태로 예쁘게 포맷팅
- [ ] 메시지 수신 → 처리 성공 로그 순차 출력
- [ ] 입찰 시 BID_UPDATE 이벤트 로그 출력
- [ ] 경매 등록 시 NEW_AUCTION 이벤트 로그 출력

### 에러 처리
- [ ] 잘못된 JSON 파싱 실패 로그
- [ ] 알 수 없는 채널 경고 로그
- [ ] 검증 실패 로그 (필수 필드 누락)
- [ ] 리스너 에러 로그 (에러 타입, 스택 트레이스 포함)

### RedisPubSubConfig 에러 핸들러
- [ ] 리스너 컨테이너 초기화 로그 출력
- [ ] 메시지 처리 중 예외 발생 시 에러 핸들러 작동

---

## 🔍 로그 분석 팁

### 1. 로그 필터링

**성공 로그만 보기:**
```bash
grep "success" bidflow-application.log
```

**에러 로그만 보기:**
```bash
grep -E "failure|error" bidflow-application.log
```

**Redis Health 로그만 보기:**
```bash
grep "Redis Health" bidflow-application.log
```

**특정 채널 로그만 보기:**
```bash
grep "auction:1" bidflow-application.log
```

### 2. 로그 통계 확인

**이벤트 타입별 카운트:**
```bash
grep -o '"eventType": "[^"]*"' bidflow-application.log | sort | uniq -c
```

**에러 타입별 카운트:**
```bash
grep -o '"errorType": "[^"]*"' bidflow-application.log | sort | uniq -c
```

### 3. JSON 로그 파싱 (jq 사용)

```bash
# 모든 에러 이벤트 추출
grep "redis_listener_error" bidflow-application.log | jq .

# 특정 채널의 처리 시간 추출
grep "redis_event_processed" bidflow-application.log | grep "auction:1" | jq '.processingTimeMs'
```

---

## 🎯 예상 결과

### 정상 작동 시 로그 순서

1. **서버 시작**
   ```
   ✅ [Redis Pub/Sub] 리스너 컨테이너 초기화 완료
   ```

2. **10초 후 - Redis 연결 체크**
   ```
   ✅ [Redis Health] 연결 상태 정상
   ```

3. **경매 등록 시**
   ```
   📢 [Redis] 새 경매 알림 발행
   📨 [Redis] 메시지 수신
   📤 [Redis→STOMP] 메시지 처리 성공
   ```

4. **입찰 시**
   ```
   📢 [Redis] 입찰 업데이트 알림 발행
   📨 [Redis] 메시지 수신
   📤 [Redis→STOMP] 메시지 처리 성공
   ```

5. **30초마다 - Redis 연결 체크 (로그 출력 안함)**

---

## 🐛 문제 해결

### 문제: Redis Health 로그가 출력되지 않음

**원인**: RedisHealthChecker 스케줄러가 작동하지 않음

**해결**:
1. `@EnableScheduling`이 메인 애플리케이션 클래스에 있는지 확인
2. RedisHealthChecker가 `@Component`로 등록되어 있는지 확인

### 문제: 구조화된 로그가 한 줄로 출력됨

**원인**: StructuredLogger의 `toJsonString` 메서드가 이전 버전

**해결**:
1. StructuredLogger.java 파일에서 `toJsonString` 메서드 확인
2. `\n`과 들여쓰기가 포함되어 있는지 확인

### 문제: Redis 메시지가 STOMP로 전달되지 않음

**원인**: 채널 매핑 또는 구독 설정 오류

**해결**:
1. RedisPubSubConfig에서 채널 구독 설정 확인
2. RedisEventListener의 `mapChannelToStompTopic` 메서드 확인
3. 프론트엔드에서 올바른 토픽을 구독하고 있는지 확인

---

## ✅ 테스트 완료 후

모든 테스트를 통과했다면 다음 사항을 확인하세요:

1. ✅ 구조화된 로그가 예쁘게 포맷팅되어 출력됨
2. ✅ Redis 연결 상태 모니터링이 정상 작동함
3. ✅ 에러 발생 시 상세한 컨텍스트 정보가 수집됨
4. ✅ 모든 에러 케이스에서 서버가 다운되지 않음

**축하합니다! Redis 리스너 강화 기능이 성공적으로 구현되었습니다!** 🎉

