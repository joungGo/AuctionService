# 이벤트 페이로드 스키마

## 📋 목차
1. [개요](#개요)
2. [공통 필드](#공통-필드)
3. [이벤트 타입별 스키마](#이벤트-타입별-스키마)
4. [페이로드 크기 최적화](#페이로드-크기-최적화)
5. [백엔드 DTO 구조](#백엔드-dto-구조)
6. [프론트엔드 타입 정의](#프론트엔드-타입-정의)

---

## 개요

### 설계 원칙

1. **최소 필요 데이터**: 각 페이지가 필요한 최소한의 데이터만 전송
2. **타입 안전성**: TypeScript 타입 정의로 컴파일 타임 검증
3. **확장성**: 새로운 필드 추가 시 하위 호환성 유지
4. **일관성**: 모든 이벤트에 공통 필드 포함

### 페이로드 크기 전략

- **Mini Payload**: 목록 페이지용 (200-300 bytes)
- **Full Payload**: 상세 페이지용 (500-800 bytes)

---

## 공통 필드

### 모든 이벤트에 포함되는 필드

```typescript
interface BaseEvent {
  eventType: EventType;      // 이벤트 타입 (필수)
  auctionId: number;         // 경매 ID (필수)
  timestamp: number;         // Unix timestamp (ms)
}

type EventType = 
  | 'NEW_AUCTION'           // 새 경매 등록
  | 'AUCTION_STATUS_CHANGE'  // 상태 변경
  | 'BID_UPDATE'            // 입찰 업데이트
  | 'AUCTION_END';          // 경매 종료
```

**필드 설명**:
- `eventType`: 클라이언트가 적절한 핸들러로 라우팅
- `auctionId`: 특정 경매를 식별하고 업데이트
- `timestamp`: 이벤트 발생 시각 (정렬, 동기화용)

---

## 이벤트 타입별 스키마

### 1. NEW_AUCTION (새 경매 등록)

#### Mini Payload (목록 페이지용)

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

**TypeScript 타입**:
```typescript
interface NewAuctionEvent extends BaseEvent {
  eventType: 'NEW_AUCTION';
  productName: string;       // 상품명
  startPrice: number;        // 시작가
  currentBid: number;        // 현재 입찰가
  status: AuctionStatus;     // 경매 상태
  startTime: string;         // 시작 시간 (ISO 8601)
  endTime: string;           // 종료 시간 (ISO 8601)
  imageUrl: string;          // 이미지 URL
  categoryName: string;      // 카테고리명
}

type AuctionStatus = 'PENDING' | 'ONGOING' | 'FINISHED';
```

**필드 설명**:
- `productName`: 경매 목록 카드에 표시
- `startPrice` / `currentBid`: 가격 정보
- `status`: 경매 상태 배지 표시
- `startTime` / `endTime`: 남은 시간 계산
- `imageUrl`: 썸네일 이미지
- `categoryName`: 카테고리 필터링 및 표시

**사용 페이지**:
- 메인 페이지 (`/`)
- 전체 경매 페이지 (`/auctions`)
- 관리자 경매 목록 (`/admin/auctions/list`)

**예상 크기**: ~250 bytes

---

### 2. AUCTION_STATUS_CHANGE (경매 상태 변경)

#### Mini Payload (목록 페이지용)

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

**TypeScript 타입**:
```typescript
interface AuctionStatusChangeEvent extends BaseEvent {
  eventType: 'AUCTION_STATUS_CHANGE';
  status: AuctionStatus;     // 새 상태
  currentBid: number;        // 현재 입찰가
  bidderNickname?: string;   // 최고 입찰자 (없을 수 있음)
  productName: string;       // 상품명
}
```

**필드 설명**:
- `status`: PENDING → ONGOING → FINISHED 상태 전환
- `currentBid`: 상태 변경 시점의 입찰가
- `bidderNickname`: 최고 입찰자 (상태 변경 시 표시용)
- `productName`: UI 업데이트 시 경매 식별용

**상태 전환 시나리오**:

1. **PENDING → ONGOING** (경매 시작)
   ```json
   {
     "eventType": "AUCTION_STATUS_CHANGE",
     "status": "ONGOING",
     "currentBid": 10000,
     "bidderNickname": null
   }
   ```

2. **ONGOING → FINISHED** (경매 종료)
   ```json
   {
     "eventType": "AUCTION_STATUS_CHANGE",
     "status": "FINISHED",
     "currentBid": 50000,
     "bidderNickname": "사용자5"
   }
   ```

**사용 페이지**:
- 메인 페이지 (`/`)
- 전체 경매 페이지 (`/auctions`)
- 경매 상세 페이지 (`/auctions/{id}`)
- 관리자 경매 목록 (`/admin/auctions/list`)

**예상 크기**: ~200 bytes

---

### 3. BID_UPDATE (입찰 업데이트)

#### Full Payload (상세 페이지용)

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

**TypeScript 타입**:
```typescript
interface BidUpdateEvent extends BaseEvent {
  eventType: 'BID_UPDATE';
  currentBid: number;        // 새로운 입찰가
  bidderNickname: string;    // 입찰자 닉네임
  productName: string;       // 상품명
}
```

**필드 설명**:
- `currentBid`: 업데이트된 최고 입찰가
- `bidderNickname`: 입찰한 사용자 닉네임
- `productName`: 알림 메시지 표시용

**사용 페이지**:
- 경매 상세 페이지 (`/auctions/{id}`)
- 입찰 페이지 (`/auctions/{id}/bid`)

**UI 업데이트 예시**:
```typescript
// 현재 입찰가 업데이트
setCurrentBid(message.currentBid);

// 최고 입찰자 표시
setBidderNickname(message.bidderNickname);

// 토스트 알림
toast.info(`${message.bidderNickname}님이 ${message.currentBid}원에 입찰했습니다.`);
```

**예상 크기**: ~180 bytes

---

### 4. AUCTION_END (경매 종료)

#### Full Payload (상세 페이지용)

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

**TypeScript 타입**:
```typescript
interface AuctionEndEvent extends BaseEvent {
  eventType: 'AUCTION_END';
  status: 'FINISHED';        // 항상 FINISHED
  winnerNickname: string;    // 낙찰자 닉네임
  winningBid: number;        // 낙찰가
  productName: string;       // 상품명
}
```

**필드 설명**:
- `status`: 항상 `FINISHED` (타입 안전성)
- `winnerNickname`: 낙찰자 정보
- `winningBid`: 최종 낙찰가
- `productName`: 축하 메시지 표시용

**사용 페이지**:
- 경매 상세 페이지 (`/auctions/{id}`)
- 입찰 페이지 (`/auctions/{id}/bid`)

**UI 업데이트 예시**:
```typescript
// 경매 종료 모달
showModal({
  title: '경매 종료',
  message: `${message.winnerNickname}님이 ${message.winningBid}원에 낙찰되었습니다!`
});

// 입찰 버튼 비활성화
setAuctionEnded(true);
```

**예상 크기**: ~190 bytes

---

## 페이로드 크기 최적화

### Mini vs Full Payload 전략

#### Mini Payload (목록 페이지)
- **목적**: 네트워크 대역폭 절약
- **포함**: 카드 UI 렌더링에 필요한 최소 정보
- **크기**: 200-300 bytes
- **사용 예**: 메인 페이지, 카테고리 페이지

#### Full Payload (상세 페이지)
- **목적**: 완전한 정보 제공
- **포함**: 상세 정보 및 히스토리
- **크기**: 500-800 bytes
- **사용 예**: 경매 상세, 입찰 페이지

### 최적화 기법

1. **필드 생략**:
   ```typescript
   // ✅ Good: 선택적 필드
   bidderNickname?: string;  // 없을 수 있음
   
   // ❌ Bad: 항상 전송
   bidderNickname: string | null;
   ```

2. **문자열 길이 제한**:
   ```java
   // 상품명 최대 100자
   if (productName.length() > 100) {
       productName = productName.substring(0, 97) + "...";
   }
   ```

3. **숫자 타입 최적화**:
   ```typescript
   // ✅ Good: JSON Number (8 bytes)
   currentBid: 50000
   
   // ❌ Bad: 문자열 (10 bytes)
   currentBid: "50000원"
   ```

---

## 백엔드 DTO 구조

### AuctionEventPayload (Java)

**위치**: `org.example.bidflow.global.messaging.dto.AuctionEventPayload`

```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionEventPayload {
    
    // 공통 필드
    private String eventType;      // NEW_AUCTION, BID_UPDATE, etc.
    private Long auctionId;
    private Long timestamp;
    
    // 경매 정보 (Mini Payload)
    private String productName;
    private Long startPrice;
    private Long currentBid;
    private String status;
    private String startTime;
    private String endTime;
    private String imageUrl;
    private String categoryName;
    
    // 입찰 정보
    private String bidderNickname;
    
    // 경매 종료 정보
    private String winnerNickname;
    private Long winningBid;
}
```

### 팩토리 메서드

```java
// Mini Payload 생성 (목록 페이지용)
public static AuctionEventPayload createMiniPayload(
    String eventType,
    Long auctionId,
    String productName,
    Long startPrice,
    Long currentBid,
    String status,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String imageUrl,
    String categoryName
) {
    return AuctionEventPayload.builder()
        .eventType(eventType)
        .auctionId(auctionId)
        .timestamp(System.currentTimeMillis())
        .productName(productName)
        .startPrice(startPrice)
        .currentBid(currentBid)
        .status(status)
        .startTime(startTime.toString())
        .endTime(endTime.toString())
        .imageUrl(imageUrl)
        .categoryName(categoryName)
        .build();
}

// Full Payload 생성 (상세 페이지용)
public static AuctionEventPayload createFullPayload(
    String eventType,
    Long auctionId,
    String productName,
    Long currentBid,
    String bidderNickname
) {
    return AuctionEventPayload.builder()
        .eventType(eventType)
        .auctionId(auctionId)
        .timestamp(System.currentTimeMillis())
        .productName(productName)
        .currentBid(currentBid)
        .bidderNickname(bidderNickname)
        .build();
}

// 경매 종료 Payload
public static AuctionEventPayload createEndPayload(
    Long auctionId,
    String productName,
    String winnerNickname,
    Long winningBid
) {
    return AuctionEventPayload.builder()
        .eventType("AUCTION_END")
        .auctionId(auctionId)
        .timestamp(System.currentTimeMillis())
        .status("FINISHED")
        .productName(productName)
        .winnerNickname(winnerNickname)
        .winningBid(winningBid)
        .build();
}
```

---

## 프론트엔드 타입 정의

### TypeScript 타입 (realtime.ts)

**위치**: `src/lib/types/realtime.ts`

```typescript
// 기본 이벤트 인터페이스
export interface BaseEvent {
  eventType: EventType;
  auctionId: number;
  timestamp: number;
}

// 이벤트 타입
export type EventType =
  | 'NEW_AUCTION'
  | 'AUCTION_STATUS_CHANGE'
  | 'BID_UPDATE'
  | 'AUCTION_END';

// 경매 상태
export type AuctionStatus = 'PENDING' | 'ONGOING' | 'FINISHED';

// 새 경매 이벤트
export interface NewAuctionEvent extends BaseEvent {
  eventType: 'NEW_AUCTION';
  productName: string;
  startPrice: number;
  currentBid: number;
  status: AuctionStatus;
  startTime: string;
  endTime: string;
  imageUrl: string;
  categoryName: string;
}

// 상태 변경 이벤트
export interface AuctionStatusChangeEvent extends BaseEvent {
  eventType: 'AUCTION_STATUS_CHANGE';
  status: AuctionStatus;
  currentBid: number;
  bidderNickname?: string;
  productName: string;
}

// 입찰 업데이트 이벤트
export interface BidUpdateEvent extends BaseEvent {
  eventType: 'BID_UPDATE';
  currentBid: number;
  bidderNickname: string;
  productName: string;
}

// 경매 종료 이벤트
export interface AuctionEndEvent extends BaseEvent {
  eventType: 'AUCTION_END';
  status: 'FINISHED';
  winnerNickname: string;
  winningBid: number;
  productName: string;
}

// 통합 이벤트 타입
export type AuctionEvent =
  | NewAuctionEvent
  | AuctionStatusChangeEvent
  | BidUpdateEvent
  | AuctionEndEvent;
```

### 타입 가드 함수

```typescript
// 이벤트 타입 체크
export function isNewAuctionEvent(event: AuctionEvent): event is NewAuctionEvent {
  return event.eventType === 'NEW_AUCTION';
}

export function isStatusChangeEvent(event: AuctionEvent): event is AuctionStatusChangeEvent {
  return event.eventType === 'AUCTION_STATUS_CHANGE';
}

export function isBidUpdateEvent(event: AuctionEvent): event is BidUpdateEvent {
  return event.eventType === 'BID_UPDATE';
}

export function isAuctionEndEvent(event: AuctionEvent): event is AuctionEndEvent {
  return event.eventType === 'AUCTION_END';
}
```

### 사용 예시

```typescript
const handleWebSocketMessage = (message: AuctionEvent) => {
  // 타입 가드를 통한 안전한 접근
  if (isNewAuctionEvent(message)) {
    // TypeScript가 NewAuctionEvent 타입임을 인식
    console.log(message.productName);  // ✅ OK
    console.log(message.categoryName); // ✅ OK
    // console.log(message.winnerNickname); // ❌ 컴파일 에러
  }
  
  if (isBidUpdateEvent(message)) {
    // TypeScript가 BidUpdateEvent 타입임을 인식
    console.log(message.currentBid);      // ✅ OK
    console.log(message.bidderNickname);  // ✅ OK
    // console.log(message.categoryName); // ❌ 컴파일 에러
  }
};
```

---

## 스키마 검증

### 백엔드 검증 (RedisEventListener)

```java
private boolean isValidEventPayload(AuctionEventPayload payload) {
    // 공통 필드 검증
    if (payload.getEventType() == null || payload.getEventType().trim().isEmpty()) {
        return false;
    }
    
    if (payload.getAuctionId() == null || payload.getAuctionId() <= 0) {
        return false;
    }
    
    // 이벤트 타입별 검증
    switch (payload.getEventType()) {
        case "NEW_AUCTION":
            return isValidNewAuctionPayload(payload);
        case "AUCTION_STATUS_CHANGE":
            return isValidStatusChangePayload(payload);
        case "BID_UPDATE":
            return isValidBidUpdatePayload(payload);
        case "AUCTION_END":
            return isValidAuctionEndPayload(payload);
        default:
            return false;
    }
}

private boolean isValidNewAuctionPayload(AuctionEventPayload payload) {
    return payload.getProductName() != null && !payload.getProductName().trim().isEmpty() &&
           payload.getStartPrice() != null && payload.getStartPrice() >= 0;
}

private boolean isValidBidUpdatePayload(AuctionEventPayload payload) {
    return payload.getCurrentBid() != null && payload.getCurrentBid() > 0 &&
           payload.getBidderNickname() != null && !payload.getBidderNickname().trim().isEmpty();
}
```

### 프론트엔드 검증 (parsers.ts)

**위치**: `src/lib/realtime/parsers.ts`

```typescript
export function parseAuctionEvent(data: unknown): AuctionEvent | null {
  try {
    const parsed = JSON.parse(data as string);
    
    // 공통 필드 검증
    if (!parsed.eventType || typeof parsed.auctionId !== 'number') {
      console.error('Invalid event: missing required fields');
      return null;
    }
    
    // 이벤트 타입별 검증
    switch (parsed.eventType) {
      case 'NEW_AUCTION':
        return validateNewAuctionEvent(parsed);
      case 'BID_UPDATE':
        return validateBidUpdateEvent(parsed);
      case 'AUCTION_STATUS_CHANGE':
        return validateStatusChangeEvent(parsed);
      case 'AUCTION_END':
        return validateAuctionEndEvent(parsed);
      default:
        console.error('Unknown event type:', parsed.eventType);
        return null;
    }
  } catch (error) {
    console.error('Failed to parse auction event:', error);
    return null;
  }
}

function validateNewAuctionEvent(data: any): NewAuctionEvent | null {
  if (!data.productName || typeof data.startPrice !== 'number') {
    return null;
  }
  return data as NewAuctionEvent;
}
```

---

## 버전 관리 및 하위 호환성

### 스키마 버전 추가 (향후)

```typescript
interface BaseEvent {
  eventType: EventType;
  auctionId: number;
  timestamp: number;
  schemaVersion?: string;  // "1.0", "1.1", etc.
}
```

### 하위 호환성 유지 전략

1. **새 필드 추가**: 항상 선택적(`?`)으로
2. **필드 제거**: Deprecated 마킹 후 최소 6개월 유지
3. **필드 타입 변경**: 새 필드 추가 + 기존 필드 Deprecated

```typescript
// ✅ Good: 하위 호환성 유지
interface BidUpdateEvent {
  currentBid: number;
  newField?: string;  // 새 필드는 선택적
}

// ❌ Bad: 하위 호환성 깨짐
interface BidUpdateEvent {
  currentBid: number;
  newField: string;   // 기존 클라이언트 에러 발생
}
```

---

## 참고 자료

- [WEBSOCKET_ARCHITECTURE.md](./WEBSOCKET_ARCHITECTURE.md) - 전체 아키텍처
- [WEBSOCKET_TOPICS.md](./WEBSOCKET_TOPICS.md) - 토픽 구독 규칙
- [JSON Schema Specification](https://json-schema.org/)
- [TypeScript Type Guards](https://www.typescriptlang.org/docs/handbook/2/narrowing.html#using-type-predicates)

---

**작성일**: 2025-10-04  
**버전**: 1.0  
**작성자**: AuctionService 개발팀

