# StructuredLogger 테스트 가이드

## 📋 테스트 준비

### 1. 백엔드 서버 실행
```bash
cd backEnd
./gradlew bootRun
```

### 2. 로그 레벨 설정 확인
`application.yml`에서 DEBUG 레벨 활성화:
```yaml
logging:
  level:
    org.example.bidflow.global.messaging: DEBUG
```

---

## 🧪 테스트 시나리오

### 테스트 1: 새 경매 생성 → Redis → STOMP 흐름 확인

#### Step 1: 관리자로 로그인
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "admin123"
}
```

**응답에서 JWT 토큰 복사**

#### Step 2: 새 경매 생성
```http
POST http://localhost:8080/api/admin/auctions
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json

{
  "productName": "테스트 상품",
  "description": "구조화된 로깅 테스트",
  "startPrice": 10000,
  "minBid": 1000,
  "categoryId": 1,
  "startTime": "2025-10-03T10:00:00",
  "endTime": "2025-10-03T18:00:00",
  "imageUrl": "https://example.com/image.jpg"
}
```

#### Step 3: 콘솔 로그 확인
```json
// 1️⃣ Redis 메시지 수신 (DEBUG)
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "main:new-auctions",
  "payloadSize": 256,
  "timestamp": 1696213200000
}

// 2️⃣ 메시지 처리 성공 (INFO)
📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "main:new-auctions",
  "eventType": "NEW_AUCTION",
  "auctionId": 123,
  "stompTopic": "/sub/main/new-auctions",
  "processingTimeMs": 15,
  "timestamp": 1696213200000
}
```

---

### 테스트 2: 입찰 → BID_UPDATE 이벤트 확인

#### Step 1: 일반 사용자로 로그인
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "user123"
}
```

#### Step 2: 입찰하기
```http
POST http://localhost:8080/api/auctions/{auctionId}/bids
Authorization: Bearer {USER_JWT_TOKEN}
Content-Type: application/json

{
  "amount": 15000
}
```

#### Step 3: 콘솔 로그 확인
```json
// BID_UPDATE 이벤트 처리
📨 [Redis] 메시지 수신: {
  "event": "redis_message_received",
  "channel": "auction:123",
  "payloadSize": 180,
  "timestamp": 1696213205000
}

📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "auction:123",
  "eventType": "BID_UPDATE",
  "auctionId": 123,
  "stompTopic": "/sub/auction/123",
  "processingTimeMs": 8,
  "timestamp": 1696213205000
}
```

---

### 테스트 3: 관리자 수동 상태 변경 → AUCTION_STATUS_CHANGE 확인

#### Step 1: 경매 상태 변경
```http
PATCH http://localhost:8080/api/admin/auctions/{auctionId}/status
Authorization: Bearer {ADMIN_JWT_TOKEN}
Content-Type: application/json

{
  "status": "ONGOING"
}
```

#### Step 2: 콘솔 로그 확인
```json
// 메인 페이지 채널 로그
📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "main:status-changes",
  "eventType": "AUCTION_STATUS_CHANGE",
  "auctionId": 123,
  "stompTopic": "/sub/main/status-changes",
  "processingTimeMs": 12,
  "timestamp": 1696213210000
}

// 카테고리 채널 로그
📤 [Redis→STOMP] 메시지 처리 성공: {
  "event": "redis_event_processed",
  "status": "success",
  "channel": "category:1:status-changes",
  "eventType": "AUCTION_STATUS_CHANGE",
  "auctionId": 123,
  "stompTopic": "/sub/category/1/status-changes",
  "processingTimeMs": 10,
  "timestamp": 1696213210000
}
```

---

## 🧪 에러 케이스 테스트

### 테스트 4: 잘못된 JSON 파싱 실패 시뮬레이션

#### 수동 Redis 메시지 발행 (Redis CLI 사용)
```bash
redis-cli
> PUBLISH main:new-auctions "invalid json {{{}"
```

#### 콘솔 로그 확인
```json
⚠️ [Redis] 메시지 파싱 실패: {
  "event": "redis_message_parsing_failed",
  "status": "failure",
  "channel": "main:new-auctions",
  "payloadLength": 20,
  "error": "Unexpected character ('{' (code 123)): ...",
  "timestamp": 1696213220000
}
```

---

### 테스트 5: 필수 필드 누락 검증 실패

#### Redis CLI로 잘못된 페이로드 발행
```bash
redis-cli
> PUBLISH main:new-auctions '{"eventType":"NEW_AUCTION","auctionId":null}'
```

#### 콘솔 로그 확인
```json
⚠️ [Redis] 메시지 검증 실패: {
  "event": "redis_message_validation_failed",
  "status": "failure",
  "eventType": "NEW_AUCTION",
  "auctionId": null,
  "reason": "auctionId 유효하지 않음",
  "timestamp": 1696213225000
}
```

---

## 📊 로그 분석 및 모니터링

### 로그 필터링 방법

#### 1. 성공한 이벤트만 필터링
```bash
# Linux/Mac
tail -f logs/bidflow-application.log | grep "redis_event_processed"

# Windows PowerShell
Get-Content -Path logs/bidflow-application.log -Wait | Select-String "redis_event_processed"
```

#### 2. 실패한 이벤트만 필터링
```bash
# 파싱 실패
tail -f logs/bidflow-application.log | grep "redis_message_parsing_failed"

# 검증 실패
tail -f logs/bidflow-application.log | grep "redis_message_validation_failed"

# STOMP 전송 실패
tail -f logs/bidflow-application.log | grep "stomp_send_failed"
```

#### 3. 특정 경매 ID만 추적
```bash
tail -f logs/bidflow-application.log | grep '"auctionId": 123'
```

#### 4. 처리 시간이 긴 이벤트 찾기
```bash
# 20ms 이상 걸린 이벤트
tail -f logs/bidflow-application.log | grep "processingTimeMs" | awk -F'"processingTimeMs": ' '{print $2}' | awk -F',' '{if($1 > 20) print $0}'
```

---

## 💡 메트릭 수집 활용

### 로그에서 추출 가능한 메트릭

| 메트릭 | 설명 | 활용 |
|--------|------|------|
| `processingTimeMs` | 메시지 처리 시간 | 성능 병목 지점 파악 |
| `event` | 이벤트 타입 | 이벤트별 발생 빈도 추적 |
| `status` | 성공/실패 여부 | 에러율 계산 |
| `channel` | Redis 채널 | 채널별 트래픽 분석 |
| `payloadSize` | 페이로드 크기 | 네트워크 대역폭 추적 |

### ELK 스택 연동 예시
```json
// Logstash 필터 설정
filter {
  if [message] =~ /^\{.*\}$/ {
    json {
      source => "message"
    }
    
    if [event] == "redis_event_processed" {
      metrics {
        meter => "events_per_second"
        add_field => { "metric_type" => "throughput" }
      }
    }
  }
}
```

---

## 🔍 디버깅 팁

### 1. DEBUG 레벨 로그 활성화
```yaml
# application.yml
logging:
  level:
    org.example.bidflow.global.messaging: DEBUG
    org.example.bidflow.global.logging: DEBUG
```

### 2. 로그 파일 위치
```
backEnd/logs/bidflow-application.log
```

### 3. 실시간 로그 모니터링
```bash
# 전체 로그
tail -f logs/bidflow-application.log

# 구조화된 로그만
tail -f logs/bidflow-application.log | grep -E '^\{.*\}$'
```

---

## ✅ 테스트 체크리스트

- [ ] 새 경매 생성 시 `redis_event_processed` 로그 확인
- [ ] 입찰 시 `BID_UPDATE` 이벤트 로그 확인
- [ ] 상태 변경 시 `AUCTION_STATUS_CHANGE` 이벤트 로그 확인
- [ ] 잘못된 JSON 파싱 실패 로그 확인
- [ ] 필수 필드 누락 검증 실패 로그 확인
- [ ] 처리 시간(`processingTimeMs`) 메트릭 수집 확인
- [ ] 로그 필터링 및 검색 기능 테스트

---

## 📞 문제 해결

### Q1: 로그가 출력되지 않아요
**A:** 로그 레벨을 DEBUG로 설정했는지 확인하세요.

### Q2: JSON 형태가 깨져서 나와요
**A:** `StructuredLogger.escapeJson()` 메서드가 특수 문자를 제대로 이스케이프하는지 확인하세요.

### Q3: 처리 시간이 너무 길게 나와요
**A:** Redis 연결 상태, 네트워크 지연, STOMP 구독자 수를 확인하세요.

