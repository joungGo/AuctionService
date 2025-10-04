# 백엔드 프로젝트 리팩토링 가이드

## 📋 개요
이 가이드는 AuctionService 백엔드 프로젝트의 패키지 구조를 도메인 중심 설계 원칙에 맞게 리팩토링하기 위한 상세한 파일 이동 계획입니다.

## 🎯 목표
- 도메인 중심 설계 원칙 완성
- controller -> service -> repository 레이어 구조 명확화
- 코드의 유지보수성과 가독성 향상

## 📁 현재 구조 분석

### 문제점
1. **data/ 폴더의 enum들이 도메인별로 분산되지 않음**
   - `AuctionStatus.java` → auction 도메인으로 이동 필요
   - `Role.java` → user 도메인으로 이동 필요

2. **global/app/ 폴더에 다양한 역할의 파일들이 혼재**
   - 설정, 컨트롤러, 서비스, 이벤트 관련 파일들이 한 곳에 모여있음
   - 역할별로 적절한 하위 폴더로 분류 필요

## 🚀 파일 이동 계획

### 1단계: data/ 폴더 enum 클래스 이동

#### 1-1. AuctionStatus.java 이동
```
현재 위치: src/main/java/org/example/bidflow/data/AuctionStatus.java
이동할 위치: src/main/java/org/example/bidflow/domain/auction/entity/AuctionStatus.java

변경할 패키지 선언:
- FROM: package org.example.bidflow.data;
- TO: package org.example.bidflow.domain.auction.entity;
```

#### 1-2. Role.java 이동
```
현재 위치: src/main/java/org/example/bidflow/data/Role.java
이동할 위치: src/main/java/org/example/bidflow/domain/user/entity/Role.java

변경할 패키지 선언:
- FROM: package org.example.bidflow.data;
- TO: package org.example.bidflow.domain.user.entity;
```

#### 1-3. data/ 폴더 삭제
- 위 두 파일 이동 완료 후 `src/main/java/org/example/bidflow/data/` 폴더 삭제

### 2단계: global/app/ 폴더 구조 정리

#### 2-1. 설정 관련 파일들 → global/config/로 이동

**이동할 파일들:**
```
AppConfig.java
RedisConfig.java
SecurityConfig.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.config;
```

#### 2-2. 컨트롤러 관련 파일들 → global/controller/로 이동

**이동할 파일들:**
```
HealthController.java
WebSocketMonitorController.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.controller;
```

#### 2-3. 서비스 관련 파일들 → global/service/로 이동

**이동할 파일들:**
```
AuctionSchedulerService.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.service;
```

#### 2-4. 이벤트 및 스케줄러 관련 파일들 → global/event/로 이동

**새로 생성할 폴더:**
```
src/main/java/org/example/bidflow/global/event/
```

**이동할 파일들:**
```
AuctionFinishedEvent.java
AuctionListenerEvent.java
AuctionScheduleInitializer.java
job/
  ├── AuctionEndJob.java
  └── AuctionStartJob.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.event;

FROM: package org.example.bidflow.global.event.job;
TO: package org.example.bidflow.global.event.job;
```

#### 2-5. WebSocket 관련 파일들 → global/ws/로 이동

**이동할 파일들:**
```
JwtPrincipalHandshakeHandler.java
StompChannelLoggingInterceptor.java
StompHandshakeHandler.java
WebSocketMessageBrokerConfig.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.ws;
```

#### 2-6. 기타 파일들 → global/utils/로 이동

**이동할 파일들:**
```
RedisCommon.java
TestSendRequest.java
```

**변경할 패키지 선언:**
```
FROM: package org.example.bidflow.global.app;
TO: package org.example.bidflow.global.utils;
```

#### 2-7. global/app/ 폴더 삭제
- 모든 파일 이동 완료 후 `src/main/java/org/example/bidflow/global/app/` 폴더 삭제

## 📋 이동 순서 (의존성 고려)

### Phase 1: Enum 클래스 이동
1. `AuctionStatus.java` 이동
2. `Role.java` 이동
3. `data/` 폴더 삭제

### Phase 2: Global/app 폴더 정리
1. 설정 파일들 이동 (AppConfig, RedisConfig, SecurityConfig)
2. 컨트롤러 파일들 이동 (HealthController, WebSocketMonitorController)
3. 서비스 파일들 이동 (AuctionSchedulerService)
4. 이벤트 폴더 생성 및 파일들 이동
5. WebSocket 관련 파일들 이동
6. 기타 파일들 이동 (RedisCommon, TestSendRequest)
7. `global/app/` 폴더 삭제

## ⚠️ 주의사항

### 패키지 선언문 변경
- 각 파일을 이동한 후 반드시 패키지 선언문을 새로운 경로에 맞게 수정
- 예: `package org.example.bidflow.data;` → `package org.example.bidflow.domain.auction.entity;`

### Import 경로 업데이트
- 파일 이동 후 다른 파일들에서 이 클래스들을 import하는 부분이 있으면 경로 업데이트 필요
- IDE의 자동 import 기능을 활용하거나 수동으로 수정

### 컴파일 오류 확인
- 각 단계별로 컴파일 오류가 없는지 확인
- 오류 발생 시 해당 단계를 완료한 후 다음 단계로 진행

## 🔍 검증 방법

### 각 단계 완료 후 확인사항
1. 파일이 올바른 위치에 있는지 확인
2. 패키지 선언문이 올바르게 수정되었는지 확인
3. 컴파일 오류가 없는지 확인 (`./gradlew compileJava`)

### 최종 검증
1. 전체 프로젝트 빌드 성공 (`./gradlew clean build`)
2. 모든 테스트 통과
3. 애플리케이션 정상 실행

## 📊 예상 결과 구조

```
src/main/java/org/example/bidflow/
├── domain/
│   ├── auction/
│   │   ├── entity/
│   │   │   ├── Auction.java
│   │   │   └── AuctionStatus.java  ← 이동됨
│   │   └── ...
│   ├── user/
│   │   ├── entity/
│   │   │   ├── User.java
│   │   │   ├── Favorite.java
│   │   │   └── Role.java  ← 이동됨
│   │   └── ...
│   └── ...
└── global/
    ├── config/          ← 설정 파일들
    │   ├── AppConfig.java
    │   ├── RedisConfig.java
    │   └── SecurityConfig.java
    ├── controller/      ← 글로벌 컨트롤러들
    │   ├── HealthController.java
    │   └── WebSocketMonitorController.java
    ├── service/         ← 글로벌 서비스들
    │   └── AuctionSchedulerService.java
    ├── event/           ← 이벤트 및 스케줄러
    │   ├── AuctionFinishedEvent.java
    │   ├── AuctionListenerEvent.java
    │   ├── AuctionScheduleInitializer.java
    │   └── job/
    │       ├── AuctionEndJob.java
    │       └── AuctionStartJob.java
    ├── ws/              ← WebSocket 관련
    │   ├── JwtPrincipalHandshakeHandler.java
    │   ├── StompChannelLoggingInterceptor.java
    │   ├── StompHandshakeHandler.java
    │   └── WebSocketMessageBrokerConfig.java
    └── utils/           ← 기타 유틸리티
        ├── RedisCommon.java
        └── TestSendRequest.java
```

## 🎯 다음 단계

파일 이동이 완료되면 AI가 다음 작업들을 수행합니다:
1. 사용하지 않는 import 및 코드 정리
2. 유틸리티 클래스 통합 및 분리
3. 중복 코드 통합 및 추상화
4. 코드 품질 개선 및 최적화
5. 최종 검증 및 빌드 테스트

---

**이 가이드를 따라 파일 이동을 완료한 후, AI에게 다음 단계 진행을 요청해주세요!** 🚀
