# AWS ALB 환경에서 WebSocket 지원을 위한 백엔드 설정

## 개요

이 문서는 AWS Application Load Balancer (ALB) 환경에서 WebSocket 연결을 지원하기 위해 백엔드 애플리케이션에 추가된 설정들을 설명합니다.

## 문제 상황

- 로컬 환경에서는 WebSocket 연결이 정상 작동
- AWS ALB 배포 환경에서는 WebSocket 연결 실패
- STOMP 연결 에러 발생
- **Vercel(HTTPS)에서 AWS ALB로의 연결 실패**
- **쿠키 기반 인증을 위해 SockJS 사용 불가**

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

### 2. WebSocketMessageBrokerConfig.java 설정 (쿠키 기반 인증)

#### 네이티브 WebSocket 설정 (SockJS 제거)

```java
@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOrigins(originConfig.getFrontend().toArray(new String[0]))
            .addInterceptors(stompHandshakeHandler);
    // 쿠키 기반 인증을 위해 SockJS 제거 - 네이티브 WebSocket 사용
}
```

**설정 설명:**

- **SockJS 제거 이유**: 쿠키 기반 인증과 호환성 문제
  - SockJS의 HTTP 폴링 단계에서 쿠키 전송이 불안정
  - 네이티브 WebSocket이 쿠키 전송에 더 안정적

### 3. 프론트엔드 설정 (Vercel)

#### 네이티브 WebSocket 클라이언트 사용

```typescript
// socket.ts
const getWsUrl = () => {
  if (window.location.protocol === 'https:') {
    // 프로덕션 환경 - 네이티브 WebSocket 사용
    return 'wss://bidflow.cloud/ws';
  }
  return 'ws://localhost:8080/ws';
};

export const connectStomp = () => {
  stompClient = new Client({
    webSocketFactory: () => {
      const wsUrl = getWsUrl();
      console.log(`[socket.ts] Creating native WebSocket connection to: ${wsUrl}`);
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
   - Vercel: `https://auction-service-fe.vercel.app`에서 네이티브 WebSocket 연결 시도
   - 목적지: `wss://bidflow.cloud/ws`

2. **WebSocket 핸드셰이크**
   - 네이티브 WebSocket 업그레이드 요청
   - 쿠키가 자동으로 전송됨

3. **ALB 처리**
   - HTTPS 리스너가 WebSocket 업그레이드 요청 수신
   - Target Group으로 요청 전달 (Sticky Session 활용)

4. **백엔드 처리**
   - StompHandshakeHandler에서 쿠키에서 JWT 토큰 추출
   - 인증 성공 시 WebSocket 연결 허용

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

### 4. ALB WebSocket 지원 확인
- **ALB 버전**: Application Load Balancer v2 이상
- **WebSocket 지원**: 기본적으로 지원 (HTTP/1.1 업그레이드)
- **연결 유지**: Idle Timeout 설정 확인 (기본 60초)

## 모니터링 및 디버깅

### 로그 확인 방법

```bash
# WebSocket 연결 관련 로그 확인
tail -f logs/bidflow-application.log | grep -E "(WebSocket|STOMP)"

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
- ALB Idle Timeout 설정 조정

## 결론

쿠키 기반 인증을 사용하는 환경에서는 네이티브 WebSocket이 SockJS보다 더 안정적입니다. AWS ALB가 WebSocket을 기본적으로 지원하므로, 적절한 설정만으로 안정적인 연결이 가능합니다.

---

**작성일**: 2024년 12월  
**적용 환경**: Vercel + AWS ALB + EC2 + Spring Boot WebSocket  
**관련 문서**: [AWS ALB WebSocket 지원 공식 문서](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html) 