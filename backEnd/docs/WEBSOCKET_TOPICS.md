# WebSocket 토픽 구독 규칙 및 명세

## 📋 목차
1. [토픽 개요](#토픽-개요)
2. [토픽 목록](#토픽-목록)
3. [페이지별 구독 전략](#페이지별-구독-전략)
4. [구독 코드 예제](#구독-코드-예제)
5. [Redis 채널 ↔ STOMP 토픽 매핑](#redis-채널--stomp-토픽-매핑)

---

## 토픽 개요

### 명명 규칙

```
/sub/{scope}/{event}
```

- **`scope`**: 데이터 범위 (main, category, auction)
- **`event`**: 이벤트 타입 (new-auctions, status-changes, 또는 경매 ID)

### 설계 원칙

1. **관심사 분리**: 페이지가 필요한 이벤트만 구독
2. **확장성**: 새로운 이벤트 타입 추가 용이
3. **명확성**: 토픽 이름만으로 용도 파악 가능

---

## 토픽 목록

### 1. 메인 페이지 토픽

#### `/sub/main/new-auctions`

**용도**: 새로 등록된 경매 알림 (모든 카테고리)

**메시지 형식**:
```json
{
  "eventType": "NEW_AUCTION",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "productName": "빈티지 카메라",
  "startPrice": 10000,
  "currentBid": 10000,
  "status": "PENDING",
  "startTime": "2025-10-05T10:00:00",
  "endTime": "2025-10-05T18:00:00",
  "imageUrl": "/uploads/camera.jpg",
  "categoryName": "전자기기"
}
```

**구독 대상**:
- 메인 페이지 (`/`)
- 관리자 경매 목록 페이지 (`/admin/auctions/list`)

**발행 시점**:
- 관리자가 새 경매 등록 시
- 즉시 발행 (지연 없음)

---

#### `/sub/main/status-changes`

**용도**: 경매 상태 변경 알림 (모든 카테고리)

**메시지 형식**:
```json
{
  "eventType": "AUCTION_STATUS_CHANGE",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "status": "ONGOING",
  "currentBid": 15000,
  "bidderNickname": "사용자1",
  "productName": "빈티지 카메라"
}
```

**구독 대상**:
- 메인 페이지 (`/`)
- 관리자 경매 목록 페이지 (`/admin/auctions/list`)

**발행 시점**:
- 경매 상태 변경 시 (PENDING → ONGOING → FINISHED)
- Quartz 스케줄러 또는 관리자 수동 변경

**상태 변경 흐름**:
```
PENDING (등록 직후)
   ↓
ONGOING (경매 시작 시간)
   ↓
FINISHED (경매 종료 시간 또는 낙찰)
```

---

### 2. 카테고리 페이지 토픽

#### `/sub/category/{categoryId}/new-auctions`

**용도**: 특정 카테고리의 새 경매 알림

**경로 파라미터**:
- `categoryId`: 카테고리 ID (숫자)

**예시**:
- `/sub/category/1/new-auctions` - 전자기기
- `/sub/category/2/new-auctions` - 패션
- `/sub/category/3/new-auctions` - 가구

**메시지 형식**: `NEW_AUCTION` 이벤트와 동일

**구독 대상**:
- 전체 경매 페이지 (`/auctions`) - 선택한 카테고리 기준
- 검색 페이지 (`/search`) - 카테고리 필터 적용 시

**발행 시점**:
- 해당 카테고리의 새 경매 등록 시
- `main:new-auctions`와 함께 발행 (동시)

---

#### `/sub/category/{categoryId}/status-changes`

**용도**: 특정 카테고리의 경매 상태 변경 알림

**경로 파라미터**:
- `categoryId`: 카테고리 ID (숫자)

**메시지 형식**: `AUCTION_STATUS_CHANGE` 이벤트와 동일

**구독 대상**:
- 전체 경매 페이지 (`/auctions`) - 선택한 카테고리 기준
- 검색 페이지 (`/search`) - 카테고리 필터 적용 시

**발행 시점**:
- 해당 카테고리의 경매 상태 변경 시
- `main:status-changes`와 함께 발행 (동시)

---

### 3. 경매 상세 페이지 토픽

#### `/sub/auction/{auctionId}`

**용도**: 특정 경매의 모든 이벤트 (입찰, 상태 변경, 종료 등)

**경로 파라미터**:
- `auctionId`: 경매 ID (숫자)

**예시**:
- `/sub/auction/123` - 경매 ID 123
- `/sub/auction/456` - 경매 ID 456

**메시지 형식** (이벤트 타입별):

**1) BID_UPDATE (입찰 업데이트)**
```json
{
  "eventType": "BID_UPDATE",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "currentBid": 25000,
  "bidderNickname": "사용자2",
  "productName": "빈티지 카메라"
}
```

**2) AUCTION_STATUS_CHANGE (상태 변경)**
```json
{
  "eventType": "AUCTION_STATUS_CHANGE",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "status": "ONGOING",
  "currentBid": 25000,
  "bidderNickname": "사용자2",
  "productName": "빈티지 카메라"
}
```

**3) AUCTION_END (경매 종료)**
```json
{
  "eventType": "AUCTION_END",
  "auctionId": 123,
  "timestamp": 1759415601234,
  "status": "FINISHED",
  "winnerNickname": "사용자2",
  "winningBid": 50000,
  "productName": "빈티지 카메라"
}
```

**구독 대상**:
- 경매 상세 페이지 (`/auctions/{auctionId}`)
- 입찰 페이지 (`/auctions/{auctionId}/bid`)

**발행 시점**:
- 입찰 발생 시 (즉시)
- 경매 상태 변경 시
- 경매 종료 시

---

## 페이지별 구독 전략

### 메인 페이지 (`/`)

**구독 토픽**:
```
/sub/main/new-auctions
/sub/main/status-changes
```

**이유**:
- 모든 카테고리의 경매 표시
- 실시간 경매 상태 업데이트 필요

**동작**:
1. 새 경매 등록 → 경매 목록에 추가
2. 상태 변경 → 해당 경매 카드 업데이트

---

### 전체 경매 페이지 (`/auctions`)

**구독 토픽** (카테고리 선택에 따라 동적):
```
// "전체" 선택 시
/sub/main/new-auctions
/sub/main/status-changes

// 특정 카테고리 선택 시 (예: 카테고리 1)
/sub/category/1/new-auctions
/sub/category/1/status-changes
```

**이유**:
- 카테고리 필터링 지원
- 선택한 카테고리의 이벤트만 수신

**동작**:
1. 카테고리 변경 → 기존 구독 해제 + 새 구독
2. 새 경매 등록 → 필터링된 목록에 추가
3. 상태 변경 → 필터링된 목록 업데이트

---

### 검색 페이지 (`/search`)

**구독 토픽** (검색 조건에 따라 동적):
```
// 카테고리 필터 없음
/sub/main/new-auctions
/sub/main/status-changes

// 카테고리 필터 있음 (예: 카테고리 2)
/sub/category/2/new-auctions
/sub/category/2/status-changes
```

**이유**:
- 검색 결과도 실시간 업데이트 필요
- 카테고리 필터에 따라 최적화

**동작**:
1. 이벤트 수신 → 검색 조건 재평가
2. 조건 일치 → 결과 목록에 추가/업데이트
3. 조건 불일치 → 무시

---

### 경매 상세 페이지 (`/auctions/{auctionId}`)

**구독 토픽**:
```
/sub/auction/{auctionId}
```

**이유**:
- 특정 경매의 모든 이벤트 필요
- 입찰 실시간 업데이트

**동작**:
1. BID_UPDATE → 현재 입찰가 업데이트
2. AUCTION_STATUS_CHANGE → 상태 배지 업데이트
3. AUCTION_END → 낙찰자 정보 표시

---

### 입찰 페이지 (`/auctions/{auctionId}/bid`)

**구독 토픽**:
```
/sub/auction/{auctionId}
```

**이유**:
- 실시간 입찰가 경쟁 정보 필요
- 다른 사용자의 입찰 즉시 반영

**동작**:
1. BID_UPDATE → 현재 입찰가 표시 업데이트
2. AUCTION_END → 경매 종료 알림

---

### 관리자 경매 목록 페이지 (`/admin/auctions/list`)

**구독 토픽**:
```
/sub/main/new-auctions
/sub/main/status-changes
```

**이유**:
- 모든 경매 모니터링 필요
- 실시간 상태 추적

**동작**:
1. 새 경매 등록 → 관리 목록에 추가
2. 상태 변경 → 관리 목록 업데이트

---

## 구독 코드 예제

### React 컴포넌트에서 구독

#### 메인 페이지
```typescript
'use client';

import { useWebSocket } from '@/app/context/WebSocketContext';
import { useEffect } from 'react';

export default function MainPage() {
  const { isConnected, subscribeToMainPage, unsubscribeFromMainPage } = useWebSocket();
  
  useEffect(() => {
    if (!isConnected) return;
    
    // 메인 페이지 토픽 구독
    const subscriptionId = subscribeToMainPage((message) => {
      console.log('Received:', message);
      
      if (message.eventType === 'NEW_AUCTION') {
        // 새 경매 추가 로직
      } else if (message.eventType === 'AUCTION_STATUS_CHANGE') {
        // 상태 변경 로직
      }
    });
    
    // 언마운트 시 구독 해제
    return () => {
      unsubscribeFromMainPage(subscriptionId);
    };
  }, [isConnected]);
  
  return <div>...</div>;
}
```

---

#### 카테고리 페이지 (동적 구독)
```typescript
'use client';

import { useWebSocket } from '@/app/context/WebSocketContext';
import { useEffect, useRef } from 'react';

export default function AuctionsPage() {
  const { isConnected, subscribeToCategoryPage, unsubscribeFromCategoryPage } = useWebSocket();
  const [selectedCategoryId, setSelectedCategoryId] = useState(null);
  const subscriptionIdRef = useRef<string | null>(null);
  
  useEffect(() => {
    if (!isConnected || !selectedCategoryId) return;
    
    // 기존 구독 해제
    if (subscriptionIdRef.current) {
      unsubscribeFromCategoryPage(subscriptionIdRef.current);
    }
    
    // 새 카테고리 구독
    const subscriptionId = subscribeToCategoryPage(
      selectedCategoryId,
      (message) => {
        console.log('Received:', message);
        // 업데이트 로직
      }
    );
    
    subscriptionIdRef.current = subscriptionId;
    
    return () => {
      if (subscriptionIdRef.current) {
        unsubscribeFromCategoryPage(subscriptionIdRef.current);
      }
    };
  }, [isConnected, selectedCategoryId]);
  
  return <div>...</div>;
}
```

---

#### 경매 상세 페이지
```typescript
'use client';

import { useWebSocket } from '@/app/context/WebSocketContext';
import { useEffect } from 'react';

export default function AuctionDetailPage({ auctionId }: { auctionId: number }) {
  const { isConnected, subscribeToAuctionDetail, unsubscribeFromAuctionDetail } = useWebSocket();
  
  useEffect(() => {
    if (!isConnected) return;
    
    // 경매 상세 토픽 구독
    const subscriptionId = subscribeToAuctionDetail(auctionId, (message) => {
      console.log('Received:', message);
      
      switch (message.eventType) {
        case 'BID_UPDATE':
          // 입찰가 업데이트
          break;
        case 'AUCTION_STATUS_CHANGE':
          // 상태 변경
          break;
        case 'AUCTION_END':
          // 경매 종료
          break;
      }
    });
    
    return () => {
      unsubscribeFromAuctionDetail(subscriptionId);
    };
  }, [isConnected, auctionId]);
  
  return <div>...</div>;
}
```

---

## Redis 채널 ↔ STOMP 토픽 매핑

### 매핑 규칙

| Redis Channel | STOMP Topic | 설명 |
|---------------|-------------|------|
| `main:new-auctions` | `/sub/main/new-auctions` | 메인 페이지 - 새 경매 |
| `main:status-changes` | `/sub/main/status-changes` | 메인 페이지 - 상태 변경 |
| `category:1:new-auctions` | `/sub/category/1/new-auctions` | 카테고리 1 - 새 경매 |
| `category:1:status-changes` | `/sub/category/1/status-changes` | 카테고리 1 - 상태 변경 |
| `category:2:new-auctions` | `/sub/category/2/new-auctions` | 카테고리 2 - 새 경매 |
| `category:2:status-changes` | `/sub/category/2/status-changes` | 카테고리 2 - 상태 변경 |
| `auction:123` | `/sub/auction/123` | 경매 123 - 모든 이벤트 |
| `auction:456` | `/sub/auction/456` | 경매 456 - 모든 이벤트 |

### 매핑 코드 (백엔드)

**위치**: `RedisEventListener.mapChannelToStompTopic()`

```java
private String mapChannelToStompTopic(String channel) {
    // 메인 페이지 토픽
    if (channel.equals("main:new-auctions")) {
        return "/sub/main/new-auctions";
    } else if (channel.equals("main:status-changes")) {
        return "/sub/main/status-changes";
    }
    
    // 카테고리 페이지 토픽
    else if (channel.startsWith("category:") && channel.endsWith(":new-auctions")) {
        String categoryId = channel.substring(9, channel.lastIndexOf(":"));
        return "/sub/category/" + categoryId + "/new-auctions";
    } else if (channel.startsWith("category:") && channel.endsWith(":status-changes")) {
        String categoryId = channel.substring(9, channel.lastIndexOf(":"));
        return "/sub/category/" + categoryId + "/status-changes";
    }
    
    // 경매 상세 페이지 토픽
    else if (channel.startsWith("auction:")) {
        String auctionId = channel.substring(8);
        return "/sub/auction/" + auctionId;
    }
    
    // 알 수 없는 채널
    return null;
}
```

---

## 구독 최적화 팁

### 1. 구독 해제 필수

```typescript
useEffect(() => {
    const subscriptionId = subscribe(...);
    
    // ✅ 반드시 언마운트 시 구독 해제
    return () => {
        unsubscribe(subscriptionId);
    };
}, [dependencies]);
```

### 2. 중복 구독 방지

```typescript
// ❌ Bad: 중복 구독 발생
useEffect(() => {
    subscribe(...);
}, [isConnected, auctionId]);

// ✅ Good: 기존 구독 해제 후 재구독
useEffect(() => {
    if (subscriptionIdRef.current) {
        unsubscribe(subscriptionIdRef.current);
    }
    const subscriptionId = subscribe(...);
    subscriptionIdRef.current = subscriptionId;
}, [isConnected, auctionId]);
```

### 3. 메시지 필터링

```typescript
const handleMessage = (message: AuctionEvent) => {
    // 현재 페이지와 관련 없는 메시지 무시
    if (message.auctionId !== currentAuctionId) {
        return;
    }
    
    // 처리 로직
};
```

---

## 문제 해결

### 문제: 메시지가 수신되지 않음

**체크리스트**:
1. WebSocket 연결 상태 확인 (`isConnected`)
2. 구독 토픽 이름 확인 (오타 여부)
3. 백엔드 로그에서 Redis → STOMP 전송 확인
4. 브라우저 개발자 도구의 WebSocket 탭 확인

### 문제: 중복 메시지 수신

**원인**:
- 구독 해제 누락
- 컴포넌트 리렌더링 시 중복 구독

**해결**:
```typescript
const subscriptionIdRef = useRef<string | null>(null);

useEffect(() => {
    if (subscriptionIdRef.current) {
        unsubscribe(subscriptionIdRef.current);
    }
    subscriptionIdRef.current = subscribe(...);
}, [dependencies]);
```

### 문제: 페이지 전환 시 구독이 유지됨

**원인**:
- `useEffect` cleanup 함수 누락

**해결**:
```typescript
useEffect(() => {
    const subscriptionId = subscribe(...);
    
    // ✅ cleanup 함수 추가
    return () => {
        unsubscribe(subscriptionId);
    };
}, []);
```

---

## 참고 자료

- [WEBSOCKET_ARCHITECTURE.md](WEBSOCKET_ARCHITECTURE.md) - 전체 아키텍처
- [EVENT_PAYLOAD_SCHEMA.md](EVENT_PAYLOAD_SCHEMA.md) - 이벤트 스키마
- [STOMP Protocol Specification](https://stomp.github.io/)

---

**작성일**: 2025-10-04  
**버전**: 1.0  
**작성자**: AuctionService 개발팀

