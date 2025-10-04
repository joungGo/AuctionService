# AuctionService - 실시간 경매 플랫폼 (Backend)

> Spring Boot 기반의 실시간 경매 서비스 백엔드

[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen)](https://spring.io/projects/spring-boot)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-blue)](https://stomp.github.io/)
[![Redis](https://img.shields.io/badge/Redis-Pub/Sub-red)](https://redis.io/)

---

## 📋 목차

- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [기술 스택](#기술-스택)
- [아키텍처](#아키텍처)
- [시작하기](#시작하기)
- [API 문서](#api-문서)
- [WebSocket 통신](#websocket-통신)
- [개발 가이드](#개발-가이드)
- [성능 최적화](#성능-최적화)
- [문제 해결](#문제-해결)

---

## 프로젝트 개요

AuctionService는 실시간 입찰 기능을 제공하는 경매 플랫폼입니다. 
WebSocket과 Redis Pub/Sub를 활용하여 수천 명의 동시 사용자에게 실시간 경매 업데이트를 제공합니다.

### 핵심 특징

- ⚡ **실시간 통신**: WebSocket (STOMP) 기반 실시간 입찰 및 상태 업데이트
- 🔄 **수평 확장**: Redis Pub/Sub를 통한 멀티 서버 환경 지원
- 📊 **구조화된 로깅**: JSON 형태의 로그로 메트릭 수집 및 분석 준비
- 🛡️ **안정성**: 에러 처리, 재시도 로직, 헬스 체크
- 🚀 **성능**: HTTP 폴링 대비 98% 이상의 트래픽 감소

---

## 주요 기능

### 경매 관리
- 경매 등록/수정/삭제 (관리자)
- 경매 상태 자동 관리 (Quartz 스케줄러)
- 카테고리별 경매 분류
- 경매 검색 및 필터링

### 실시간 입찰
- WebSocket 기반 실시간 입찰
- 입찰 히스토리 추적
- 낙찰자 자동 결정
- 입찰 검증 (최소 입찰 단위, 중복 입찰 방지)

### 사용자 관리
- JWT 기반 인증/인가
- 쿠키 기반 세션 관리
- 역할 기반 접근 제어 (ADMIN, USER)
- 사용자 프로필 관리

### 실시간 알림
- 새 경매 등록 알림
- 경매 상태 변경 알림
- 입찰 업데이트 알림
- 경매 종료 및 낙찰 알림

---

## 기술 스택

### Core
- **Java 21** - 프로그래밍 언어
- **Spring Boot 3.4.3** - 애플리케이션 프레임워크
- **Gradle** - 빌드 도구

### Database
- **MySQL** - 관계형 데이터베이스
- **JPA/Hibernate** - ORM
- **Redis** - 캐싱 및 메시지 브로커

### 실시간 통신
- **Spring WebSocket** - WebSocket 지원
- **STOMP** - 메시지 프로토콜
- **Redis Pub/Sub** - 이벤트 브로커

### 스케줄링
- **Quartz Scheduler** - 경매 상태 자동 관리

### 보안
- **Spring Security** - 인증/인가
- **JWT** - 토큰 기반 인증

### 모니터링 (예정)
- **Prometheus** - 메트릭 수집
- **Grafana** - 대시보드

---

## 아키텍처

### 전체 시스템 구조

```
┌─────────────┐     WebSocket      ┌──────────────┐
│   Client    │ <───────────────> │ Spring Boot  │
│ (Browser)   │      (STOMP)       │   Server     │
└─────────────┘                    └───────┬──────┘
                                           │
                         ┌─────────────────┼──────────────┐
                         │                 │              │
                    ┌────▼────┐     ┌─────▼────┐   ┌─────▼─────┐
                    │  MySQL  │     │  Redis   │   │  Quartz   │
                    │   DB    │     │ Pub/Sub  │   │ Scheduler │
                    └─────────┘     └──────────┘   └───────────┘
```

### WebSocket 메시지 흐름

```
비즈니스 로직 → EventPublisher → Redis Pub/Sub → RedisEventListener → STOMP Topic → Client
```

자세한 아키텍처는 [WEBSOCKET_ARCHITECTURE.md](WEBSOCKET_ARCHITECTURE.md)를 참조하세요.

---

## 시작하기

### 필수 요구사항

- **JDK 21** 이상
- **MySQL 8.0** 이상
- **Redis 7.0** 이상
- **Gradle 8.0** 이상 (또는 wrapper 사용)

### 환경 설정

1. **저장소 클론**
   ```bash
   git clone https://github.com/your-org/AuctionService.git
   cd AuctionService/backEnd
   ```

2. **데이터베이스 설정**
   ```sql
   CREATE DATABASE auction_service;
   CREATE USER 'auction_user'@'localhost' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON auction_service.* TO 'auction_user'@'localhost';
   ```

3. **Redis 설치 및 실행**
   ```bash
   # Windows (Chocolatey)
   choco install redis-64
   redis-server
   
   # Mac (Homebrew)
   brew install redis
   brew services start redis
   
   # Docker
   docker run -d -p 6379:6379 redis:7-alpine
   ```

4. **애플리케이션 설정**
   
   `src/main/resources/application.yml` 파일을 환경에 맞게 수정:
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/auction_service
       username: auction_user
       password: password
     
     data:
       redis:
         host: localhost
         port: 6379
   
     jpa:
       hibernate:
         ddl-auto: update  # 프로덕션에서는 validate 사용
   ```

5. **애플리케이션 실행**
   ```bash
   # Gradle wrapper 사용
   ./gradlew bootRun
   
   # 또는 빌드 후 실행
   ./gradlew build
   java -jar build/libs/bidflow-0.0.1-SNAPSHOT.jar
   ```

6. **실행 확인**
   - 서버: http://localhost:8080
   - Health Check: http://localhost:8080/actuator/health

---

## API 문서

### 경매 API

#### 경매 목록 조회
```http
GET /api/auctions
Query Parameters:
  - categoryId: Long (선택) - 카테고리 ID
  - status: String (선택) - PENDING, ONGOING, FINISHED
  - page: int (기본값: 0)
  - size: int (기본값: 20)

Response:
{
  "code": "200",
  "msg": "경매 목록 조회 성공",
  "data": [
    {
      "auctionId": 1,
      "productName": "빈티지 카메라",
      "currentBid": 50000,
      "status": "ONGOING",
      ...
    }
  ]
}
```

#### 경매 상세 조회
```http
GET /api/auctions/{auctionId}

Response:
{
  "code": "200",
  "msg": "경매 상세 조회 성공",
  "data": {
    "auctionId": 1,
    "productName": "빈티지 카메라",
    "description": "1980년대 필름 카메라",
    "currentBid": 50000,
    "bidCount": 15,
    ...
  }
}
```

#### 경매 등록 (관리자)
```http
POST /api/admin/auctions
Authorization: Bearer {token}

Request Body:
{
  "categoryId": 1,
  "productName": "빈티지 카메라",
  "description": "1980년대 필름 카메라",
  "startPrice": 10000,
  "startTime": "2025-10-05T10:00:00",
  "endTime": "2025-10-05T18:00:00"
}

Response:
{
  "code": "200",
  "msg": "경매 등록 성공",
  "data": { ... }
}
```

### 입찰 API

#### 입찰하기
```http
POST /api/bids
Authorization: Bearer {token}

Request Body:
{
  "auctionId": 1,
  "amount": 55000
}

Response:
{
  "code": "200",
  "msg": "입찰 성공",
  "data": {
    "bidId": 123,
    "amount": 55000,
    ...
  }
}
```

#### 입찰 히스토리 조회
```http
GET /api/auctions/{auctionId}/bids

Response:
{
  "code": "200",
  "msg": "입찰 히스토리 조회 성공",
  "data": [
    {
      "bidId": 123,
      "amount": 55000,
      "bidderNickname": "사용자1",
      "bidTime": "2025-10-05T14:30:00"
    },
    ...
  ]
}
```

---

## WebSocket 통신

### 연결 엔드포인트

```
ws://localhost:8080/ws
```

프로덕션 환경에서는 `wss://` (SSL) 사용 필수

### STOMP 구독 토픽

#### 메인 페이지 토픽
```
/sub/main/new-auctions        # 새 경매 등록
/sub/main/status-changes       # 경매 상태 변경
```

#### 카테고리 페이지 토픽
```
/sub/category/{categoryId}/new-auctions      # 카테고리별 새 경매
/sub/category/{categoryId}/status-changes    # 카테고리별 상태 변경
```

#### 경매 상세 페이지 토픽
```
/sub/auction/{auctionId}       # 특정 경매의 모든 이벤트
```

### 메시지 형식

#### NEW_AUCTION 이벤트
```json
{
  "eventType": "NEW_AUCTION",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "productName": "빈티지 카메라",
  "startPrice": 10000,
  "currentBid": 10000,
  "status": "PENDING",
  "categoryName": "전자기기"
}
```

#### BID_UPDATE 이벤트
```json
{
  "eventType": "BID_UPDATE",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "currentBid": 55000,
  "bidderNickname": "사용자1"
}
```

자세한 내용은 다음 문서를 참조하세요:
- [WebSocket 아키텍처](WEBSOCKET_ARCHITECTURE.md)
- [토픽 구독 규칙](WEBSOCKET_TOPICS.md)
- [이벤트 페이로드 스키마](EVENT_PAYLOAD_SCHEMA.md)

---

## 개발 가이드

### 프로젝트 구조

```
src/main/java/org/example/bidflow/
├── domain/
│   ├── auction/        # 경매 도메인
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── service/
│   │   ├── controller/
│   │   └── dto/
│   ├── bid/            # 입찰 도메인
│   ├── user/           # 사용자 도메인
│   └── category/       # 카테고리 도메인
├── global/
│   ├── config/         # 설정 (WebSocket, Redis, Security)
│   ├── messaging/      # 메시징 (EventPublisher, Listener)
│   ├── ws/             # WebSocket 핸들러
│   ├── logging/        # 구조화된 로깅
│   ├── monitoring/     # 헬스 체크
│   └── filter/         # 필터 (JWT, Rate Limiting)
└── BidflowApplication.java
```

### 개발 규칙

#### 1. 엔티티 설계
```java
@Entity
@Table(name = "AUCTION")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AUCTION_ID")
    private Long auctionId;
    
    @Column(name = "START_PRICE", nullable = false)
    private Long startPrice;
    
    // ...
}
```

#### 2. 서비스 레이어
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {
    
    private final AuctionRepository auctionRepository;
    private final EventPublisher eventPublisher;
    
    @Transactional
    public RsData<AuctionResponse> createAuction(AuctionCreateRequest request) {
        // 1. 비즈니스 로직
        Auction auction = auctionRepository.save(...);
        
        // 2. 이벤트 발행
        eventPublisher.publishNewAuction(auction.getAuctionId(), ...);
        
        // 3. 응답 반환
        return RsData.of("200", "경매 등록 성공", ...);
    }
}
```

#### 3. 이벤트 발행
```java
// ✅ Good: EventPublisher 인터페이스 사용
eventPublisher.publishNewAuction(auctionId, categoryId, ...);

// ❌ Bad: RedisTemplate 직접 사용
redisTemplate.convertAndSend("main:new-auctions", ...);
```

#### 4. 에러 처리
```java
try {
    // 비즈니스 로직
} catch (Exception e) {
    // 구조화된 로깅
    StructuredLogger.logRedisListenerError(channel, payload, errorType, e.getMessage(), stackTrace);
    throw new ServiceException("처리 실패", e);
}
```

### 코딩 컨벤션

- **네이밍**: camelCase (메서드, 변수), PascalCase (클래스)
- **패키지**: 도메인 중심 구조
- **Lombok**: 보일러플레이트 코드 최소화
- **주석**: JavaDoc 스타일, 왜(Why)에 집중

---

## 성능 최적화

### HTTP 폴링 → WebSocket 전환 효과

| 항목 | Before (폴링) | After (WebSocket) | 개선율 |
|------|---------------|-------------------|--------|
| HTTP 요청 수 | 600 req/s | ~10 req/s | **98%** ↓ |
| DB 쿼리 수 | 800 queries/s | ~20 queries/s | **97%** ↓ |
| 네트워크 트래픽 | 30 MB/s | 3 KB/s | **99.99%** ↓ |
| 실시간성 | 0~5초 지연 | < 100ms | **50배** ↑ |

*동시 접속자 1000명 기준*

### 추가 최적화 전략

1. **데이터베이스**
   - 인덱스 최적화 (AUCTION_ID, STATUS, START_TIME)
   - 커넥션 풀 튜닝 (HikariCP)
   - 읽기 전용 트랜잭션 분리

2. **캐싱**
   - Redis 캐싱 (경매 목록, 카테고리)
   - 캐시 무효화 전략 (이벤트 기반)

3. **비동기 처리**
   - 이벤트 발행 비동기화
   - 대용량 알림 배치 처리

---

## 문제 해결

### WebSocket 연결 실패

**증상**: 클라이언트가 WebSocket 연결을 수립하지 못함

**원인 및 해결**:
1. CORS 설정 확인
   ```java
   registry.addEndpoint("/ws")
           .setAllowedOriginPatterns("*")  // 프로덕션에서는 특정 도메인만
           .withSockJS();
   ```

2. 프록시/로드 밸런서 설정 (nginx)
   ```nginx
   location /ws {
       proxy_pass http://backend;
       proxy_http_version 1.1;
       proxy_set_header Upgrade $http_upgrade;
       proxy_set_header Connection "upgrade";
   }
   ```

---

### Redis 연결 실패

**증상**: `Connection refused` 에러 로그

**해결**:
1. Redis 서버 실행 상태 확인
   ```bash
   redis-cli ping
   # PONG 응답 확인
   ```

2. application.yml 설정 확인
   ```yaml
   spring:
     data:
       redis:
         host: localhost  # Redis 서버 주소
         port: 6379       # Redis 포트
   ```

---

### 메시지가 클라이언트에 전달되지 않음

**체크리스트**:
1. Redis 연결 상태 확인
   ```bash
   # 로그에서 확인
   grep "Redis Health" logs/bidflow-application.log
   ```

2. 채널 구독 설정 확인 (`RedisPubSubConfig`)
   ```java
   container.addMessageListener(listener, new ChannelTopic("main:new-auctions"));
   ```

3. 토픽 매핑 확인 (`RedisEventListener.mapChannelToStompTopic()`)

4. 프론트엔드 구독 토픽 확인
   ```typescript
   stompClient.subscribe('/sub/main/new-auctions', callback);
   ```

---

## 로그 분석

### 구조화된 로그 확인

```bash
# 성공 로그
grep "success" logs/bidflow-application.log

# 에러 로그
grep -E "failure|error" logs/bidflow-application.log

# Redis Health 로그
grep "Redis Health" logs/bidflow-application.log

# 특정 채널 로그
grep "auction:1" logs/bidflow-application.log
```

### 로그 통계

```bash
# 이벤트 타입별 카운트
grep -o '"eventType": "[^"]*"' logs/bidflow-application.log | sort | uniq -c

# 에러 타입별 카운트
grep -o '"errorType": "[^"]*"' logs/bidflow-application.log | sort | uniq -c
```

---

## 배포

### 프로덕션 빌드

```bash
# JAR 빌드
./gradlew clean build

# 빌드된 파일 위치
build/libs/bidflow-0.0.1-SNAPSHOT.jar
```

### Docker 배포

```bash
# Docker 이미지 빌드
docker build -t auction-service:latest .

# 컨테이너 실행
docker run -d \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/auction_service \
  -e SPRING_DATA_REDIS_HOST=redis \
  auction-service:latest
```

### 환경변수

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `SPRING_PROFILES_ACTIVE` | 활성 프로파일 | `prod` |
| `SPRING_DATASOURCE_URL` | 데이터베이스 URL | `jdbc:mysql://...` |
| `SPRING_DATA_REDIS_HOST` | Redis 호스트 | `redis` |
| `JWT_SECRET` | JWT 시크릿 키 | `your-secret-key` |

---

## 관련 문서

### 아키텍처 및 설계
- [WebSocket 아키텍처](WEBSOCKET_ARCHITECTURE.md)
- [WebSocket 토픽 구독 규칙](WEBSOCKET_TOPICS.md)
- [이벤트 페이로드 스키마](EVENT_PAYLOAD_SCHEMA.md)
- [HTTP 폴링 → WebSocket 마이그레이션 가이드](MIGRATION_FROM_POLLING.md)

### 테스트 및 운영
- [Redis 리스너 테스트 가이드](REDIS_LISTENER_TEST_GUIDE.md)
- [구조화된 로깅 테스트 가이드](STRUCTURED_LOGGING_TEST_GUIDE.md)

---

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

---

## 문의

- **이메일**: dev@auctionservice.com
- **이슈 트래커**: https://github.com/your-org/AuctionService/issues

---

**작성일**: 2025-10-04  
**버전**: 2.0 (WebSocket 전환 완료)  
**작성자**: AuctionService 개발팀

