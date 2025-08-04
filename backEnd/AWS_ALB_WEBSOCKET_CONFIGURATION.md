# AWS ALB 환경에서 WebSocket 지원을 위한 백엔드 설정

## 개요

이 문서는 AWS Application Load Balancer (ALB) 환경에서 WebSocket 연결을 지원하기 위해 백엔드 애플리케이션에 추가된 설정들을 설명합니다.

## 문제 상황

- 로컬 환경에서는 WebSocket 연결이 정상 작동
- AWS ALB 배포 환경에서는 WebSocket 연결 실패
- STOMP 연결 에러 발생
- **Vercel(HTTPS)에서 AWS ALB로의 연결 실패**

## 해결 방안

### 1. application.yml 설정 추가

#### ALB 환경을 위한 Tomcat 설정

```yaml
server:
  tomcat:
    # WebSocket을 위한 추가 설정
    max-http-header-size: 8192
    connection-timeout: 20000
  # 프록시 헤더 처리 설정 (ALB 환경에서 필수)
  forward-headers-strategy: native
```

**설정 설명:**

- `max-http-header-size: 8192`: HTTP 헤더 크기 제한을 8KB로 설정
  - ALB를 통과하면서 추가되는 헤더들을 수용하기 위함
  - WebSocket 핸드셰이크 시 필요한 헤더 크기 확보

- `connection-timeout: 20000`: 연결 타임아웃을 20초로 설정
  - ALB와 백엔드 간의 연결 유지 시간 확보
  - WebSocket 연결 설정 시간 여유 제공

- `forward-headers-strategy: native`: 프록시 헤더 처리 전략 설정
  - ALB가 전달하는 `X-Forwarded-For`, `X-Forwarded-Proto` 헤더 처리
  - 클라이언트의 실제 IP와 프로토콜 정보 획득
  - Spring Boot 2.2+ 에서 권장하는 설정 방식

### 2. WebSocketMessageBrokerConfig.java 설정 강화

#### SockJS 설정 최적화 (Vercel 호환성)

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins(originConfig.getFrontend().toArray(new String[0]))
            .addInterceptors(stompHandshakeHandler)
            .withSockJS()  // Vercel 환경에서 안정성을 위해 필수
            .setStreamBytesLimit(512 * 1024) // ALB를 위한 설정
            .setHttpMessageCacheSize(1000)
            .setDisconnectDelay(30 * 1000);
}
```

**설정 설명:**

- `withSockJS()`: Vercel 환경에서 안정적인 연결을 위해 필수
  - ALB의 WebSocket 제한을 우회
  - Fallback 메커니즘 제공

- `setStreamBytesLimit(512 * 1024)`: 스트림 바이트 제한을 512KB로 설정
  - ALB 환경에서 대용량 메시지 처리 지원
  - WebSocket 프레임 크기 제한 해제

- `setHttpMessageCacheSize(1000)`: HTTP 메시지 캐시 크기를 1000으로 설정
  - 다중 클라이언트 연결 시 성능 최적화
  - 메시지 버퍼링 효율성 향상

- `setDisconnectDelay(30 * 1000)`: 연결 해제 지연 시간을 30초로 설정
  - ALB의 연결 유지 시간과 동기화
  - 갑작스러운 연결 해제 방지

### 3. 프론트엔드 설정 (Vercel)

#### SockJS 클라이언트 사용

```typescript
// socket.ts
import SockJS from "sockjs-client";

const getWsUrl = () => {
  if (window.location.protocol === 'https:') {
    // 프로덕션 환경 - SockJS 사용
    return 'https://bidflow.cloud/ws';
  }
  return 'http://localhost:8080/ws';
};

export const connectStomp = () => {
  stompClient = new Client({
    webSocketFactory: () => {
      const wsUrl = getWsUrl();
      
      // 프로덕션 환경에서는 SockJS 사용
      if (window.location.protocol === 'https:') {
        return new SockJS(wsUrl);
      }
      
      // 개발 환경에서는 네이티브 WebSocket 사용
      return new WebSocket(wsUrl);
    },
    // ... 기타 설정
  });
};
```

### 4. CORS 설정 강화

#### application.yml origin 설정

```yaml
origin:
  ip:
    frontend:
      - https://auction-service-fe.vercel.app
      - https://*.vercel.app
      - https://bidflow.cloud
      - wss://bidflow.cloud
      # ... 기타 도메인들
```

## Vercel + AWS ALB 환경에서의 WebSocket 동작 방식

1. **클라이언트 연결 요청**
   - Vercel: `https://auction-service-fe.vercel.app`에서 SockJS 연결 시도
   - 목적지: `https://bidflow.cloud/ws`

2. **SockJS 핸드셰이크**
   - SockJS가 HTTP 폴링으로 시작하여 WebSocket으로 업그레이드
   - ALB의 WebSocket 제한을 우회

3. **ALB 처리**
   - HTTPS 리스너가 SockJS 요청 수신
   - Target Group으로 요청 전달 (Sticky Session 활용)

4. **백엔드 처리**
   - SockJS 설정으로 안정적인 연결 처리
   - JWT 토큰 검증 및 사용자 세션 생성

5. **메시지 전송**
   - STOMP 프로토콜을 통한 양방향 통신
   - 입찰 메시지 실시간 브로드캐스팅

## AWS ALB 설정 확인 사항

### 1. Target Group 설정
- **Protocol**: HTTP (WebSocket은 HTTP 업그레이드)
- **Port**: 8080 (백엔드 애플리케이션 포트)
- **Health Check Path**: `/actuator/health`
- **Sticky Sessions**: 활성화 (WebSocket 연결 유지)

### 2. Listener 설정
- **Protocol**: HTTPS:443
- **Default Action**: Target Group으로 라우팅
- **SSL Certificate**: 유효한 인증서 설정

### 3. Security Group 설정
- **ALB Security Group**:
  - Inbound: HTTPS(443) from 0.0.0.0/0
  - Outbound: HTTP(8080) to EC2 Security Group

- **EC2 Security Group**:
  - Inbound: HTTP(8080) from ALB Security Group
  - Outbound: All traffic

## 모니터링 및 디버깅

### 로그 확인 방법

```bash
# WebSocket 연결 관련 로그 확인
tail -f logs/bidflow-application.log | grep -E "(WebSocket|STOMP|SockJS)"

# ALB 관련 로그 확인
tail -f logs/bidflow-application.log | grep -E "(X-Forwarded|Forward)"

# CORS 관련 로그 확인
tail -f logs/bidflow-application.log | grep -E "(CORS|Origin)"
```

### 성능 모니터링

```yaml
# application.yml에 추가 권장 설정
logging:
  level:
    org.springframework.web.socket: DEBUG
    org.springframework.messaging: DEBUG
    org.springframework.web.socket.sockjs: DEBUG
```

## 추가 고려사항

### 1. 보안 설정
- ALB 보안 그룹: HTTPS(443) 포트 허용
- EC2 보안 그룹: ALB에서 8080 포트 허용
- CORS 설정: Vercel 도메인 명시적 허용

### 2. 성능 최적화
- ALB Target Group에서 Sticky Sessions 활성화
- Health Check 경로: `/actuator/health`
- Connection Pool 설정 모니터링

### 3. 장애 대응
- WebSocket 연결 수 제한 고려
- 메모리 사용량 모니터링
- SockJS Fallback 메커니즘 활용

## 결론

Vercel과 AWS ALB 환경에서 안정적인 WebSocket 연결을 위해서는 SockJS 사용이 필수적입니다. 이는 ALB의 WebSocket 제한을 우회하고, Vercel의 HTTPS 환경에서 안정적인 연결을 보장합니다.

---

**작성일**: 2024년 12월  
**적용 환경**: Vercel + AWS ALB + EC2 + Spring Boot WebSocket  
**관련 문서**: [AWS ALB WebSocket 지원 공식 문서](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html) 