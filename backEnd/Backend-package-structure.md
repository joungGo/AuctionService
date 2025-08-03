# BidFlow 백엔드 패키지 및 클래스 구조

```
src/main/java/org/example/bidflow/
|
|__ BidFlowApplication.java - Spring Boot 메인 애플리케이션 클래스, JPA Auditing과 캐싱, 스케줄링 활성화
|
|__ data/
|   |__ AuctionStatus.java - 경매 상태(ONGOING, UPCOMING, FINISHED)를 정의하는 Enum 클래스
|   |__ Role.java - 사용자 역할(USER, ADMIN)을 정의하는 Enum 클래스
|
|__ domain/
|   |
|   |__ auction/ (경매 도메인)
|   |   |__ controller/
|   |   |   |__ AdminAuctionController.java - 관리자용 경매 관리 REST API 컨트롤러
|   |   |   |__ AuctionController.java - 일반 사용자용 경매 조회 및 종료 REST API 컨트롤러
|   |   |
|   |   |__ dto/
|   |   |   |__ AuctionAdminResponse.java - 관리자용 경매 목록 응답 DTO
|   |   |   |__ AuctionBidRequest.java - 경매 입찰 요청 DTO
|   |   |   |__ AuctionCheckResponse.java - 경매 목록 조회 응답 DTO
|   |   |   |__ AuctionCreateResponse.java - 경매 생성 응답 DTO
|   |   |   |__ AuctionDetailResponse.java - 경매 상세 조회 응답 DTO
|   |   |   |__ AuctionRequest.java - 경매 생성 요청 DTO
|   |   |   |__ AuctionResponse.java - 경매 기본 응답 DTO
|   |   |
|   |   |__ entity/
|   |   |   |__ Auction.java - 경매 정보를 저장하는 JPA 엔티티 클래스
|   |   |
|   |   |__ repository/
|   |   |   |__ AuctionRepository.java - 경매 데이터 접근을 위한 JPA Repository 인터페이스
|   |   |
|   |   |__ service/
|   |       |__ AuctionService.java - 경매 관련 비즈니스 로직 처리 서비스 클래스
|   |
|   |__ bid/ (입찰 도메인)
|   |   |__ controller/
|   |   |   |__ BidController.java - 입찰 처리 REST API 컨트롤러
|   |   |
|   |   |__ dto/
|   |   |   |__ model/
|   |   |       |__ request/
|   |   |       |   |__ redis/
|   |   |       |       |__ StringRequest.java - Redis 문자열 요청 DTO
|   |   |       |
|   |   |       |__ response/
|   |   |           |__ BidCreateResponse.java - 입찰 생성 응답 DTO
|   |   |           |__ redis/
|   |   |           |   |__ StringResponse.java - Redis 문자열 응답 DTO
|   |   |           |__ webSocket/
|   |   |               |__ WebSocketResponse.java - 웹소켓 응답 DTO
|   |   |
|   |   |__ entity/
|   |   |   |__ Bid.java - 입찰 정보를 저장하는 JPA 엔티티 클래스
|   |   |
|   |   |__ repository/
|   |   |   |__ BidRepository.java - 입찰 데이터 접근을 위한 JPA Repository 인터페이스
|   |   |
|   |   |__ service/
|   |       |__ BidService.java - 입찰 관련 비즈니스 로직 처리 서비스 클래스, Redis 기반 실시간 입찰 처리
|   |
|   |__ product/ (상품 도메인)
|   |   |__ controller/
|   |   |   |__ ProductController.java - 상품 관리 REST API 컨트롤러
|   |   |
|   |   |__ dto/
|   |   |   |__ ProductResponse.java - 상품 정보 응답 DTO
|   |   |
|   |   |__ entity/
|   |   |   |__ Product.java - 상품 정보를 저장하는 JPA 엔티티 클래스
|   |   |
|   |   |__ repository/
|   |   |   |__ ProductRepository.java - 상품 데이터 접근을 위한 JPA Repository 인터페이스
|   |   |
|   |   |__ service/
|   |       |__ ProductService.java - 상품 관련 비즈니스 로직 처리 서비스 클래스
|   |
|   |__ user/ (사용자 도메인)
|   |   |__ controller/
|   |   |   |__ UserController.java - 사용자 인증 및 관리 REST API 컨트롤러
|   |   |
|   |   |__ dto/
|   |   |   |__ EmailSendRequest.java - 이메일 전송 요청 DTO
|   |   |   |__ EmailVerificationRequest.java - 이메일 인증 요청 DTO
|   |   |   |__ UserCheckRequest.java - 사용자 조회 요청 DTO
|   |   |   |__ UserPutRequest.java - 사용자 정보 수정 요청 DTO
|   |   |   |__ UserSignInRequest.java - 사용자 로그인 요청 DTO
|   |   |   |__ UserSignInResponse.java - 사용자 로그인 응답 DTO
|   |   |   |__ UserSignUpRequest.java - 사용자 회원가입 요청 DTO
|   |   |   |__ UserSignUpResponse.java - 사용자 회원가입 응답 DTO
|   |   |
|   |   |__ entity/
|   |   |   |__ User.java - 사용자 정보를 저장하는 JPA 엔티티 클래스
|   |   |
|   |   |__ repository/
|   |   |   |__ UserRepository.java - 사용자 데이터 접근을 위한 JPA Repository 인터페이스
|   |   |
|   |   |__ service/
|   |       |__ EmailService.java - 이메일 인증 관련 비즈니스 로직 처리 서비스 클래스
|   |       |__ JwtBlacklistService.java - JWT 토큰 블랙리스트 관리 서비스 클래스
|   |       |__ UserService.java - 사용자 관련 비즈니스 로직 처리 서비스 클래스, 회원가입/로그인/정보수정
|   |
|   |__ winner/ (낙찰자 도메인)
|       |__ controller/
|       |   |__ WinnerController.java - 낙찰자 조회 REST API 컨트롤러
|       |
|       |__ dto/
|       |   |__ WinnerCheckResponse.java - 낙찰자 조회 응답 DTO
|       |
|       |__ entity/
|       |   |__ Winner.java - 낙찰자 정보를 저장하는 JPA 엔티티 클래스
|       |
|       |__ repository/
|       |   |__ WinnerRepository.java - 낙찰자 데이터 접근을 위한 JPA Repository 인터페이스
|       |
|       |__ service/
|           |__ WinnerService.java - 낙찰자 관련 비즈니스 로직 처리 서비스 클래스
|
|__ global/
    |
    |__ annotation/
    |   |__ HasRole.java - 역할 기반 접근 제어를 위한 커스텀 어노테이션
    |
    |__ app/
    |   |__ AppConfig.java - 애플리케이션 전반적인 설정 클래스
    |   |__ AuctionFinishedEvent.java - 경매 종료 이벤트 처리 클래스
    |   |__ AuctionListenerEvent.java - 경매 이벤트 리스너 클래스
    |   |__ AuctionSchedulerEvent.java - 경매 스케줄링 이벤트 처리 클래스
    |   |__ HealthController.java - 애플리케이션 헬스체크 컨트롤러
    |   |__ RedisCommon.java - Redis 공통 작업 처리 유틸리티 클래스
    |   |__ RedisConfig.java - Redis 설정 클래스
    |   |__ SecurityConfig.java - Spring Security 보안 설정 클래스
    |   |__ StompHandshakeHandler.java - STOMP 웹소켓 핸드셰이크 처리 클래스
    |   |__ WebSocketMessageBrokerConfig.java - 웹소켓 메시지 브로커 설정 클래스
    |
    |__ aspect/
    |   |__ PerformanceLoggingAspect.java - 성능 로깅을 위한 AOP Aspect 클래스
    |   |__ RoleAspect.java - 역할 기반 접근 제어를 위한 AOP Aspect 클래스
    |
    |__ config/
    |   |__ OriginConfig.java - CORS Origin 설정 클래스
    |
    |__ dto/
    |   |__ RsData.java - 공통 API 응답 래퍼 DTO 클래스
    |
    |__ exception/
    |   |__ GlobalExceptionAdvisor.java - 전역 예외 처리 핸들러 클래스, JWT/DB/HTTP 예외 통합 처리
    |   |__ ServiceException.java - 비즈니스 로직 커스텀 예외 클래스
    |
    |__ filter/
    |   |__ CorsConfig.java - CORS 필터 설정 클래스
    |   |__ JwtAuthenticationFilter.java - JWT 토큰 인증 필터 클래스
    |
    |__ messaging/
    |   |__ dto/
    |   |   |__ MessagePayload.java - 메시지 페이로드 DTO 클래스
    |   |
    |   |__ listener/
    |   |   |__ MessageListener.java - 메시지 리스너 인터페이스
    |   |
    |   |__ publisher/
    |   |   |__ MessagePublisher.java - 메시지 발행 인터페이스
    |   |   |__ RedisMessagePublisher.java - Redis Pub/Sub 메시지 발행 구현체 클래스
    |   |
    |   |__ subscriber/
    |       |__ MessageSubscriber.java - 메시지 구독 인터페이스
    |       |__ RedisMessageSubscriber.java - Redis Pub/Sub 메시지 구독 구현체 클래스
    |
    |__ utils/
        |__ JwtProvider.java - JWT 토큰 생성/검증/파싱 유틸리티 클래스
        |__ LoggingUtil.java - 로깅 관련 유틸리티 클래스

src/main/resources/
|__ application.yml - Spring Boot 애플리케이션 설정 파일
```

## 프로젝트 특징
- **도메인 중심 설계**: 경매, 입찰, 사용자, 낙찰자, 상품 도메인으로 명확히 분리
- **실시간 처리**: Redis와 WebSocket을 활용한 실시간 입찰 시스템
- **보안**: JWT 기반 인증/인가 및 역할 기반 접근 제어
- **메시징**: Redis Pub/Sub 기반 메시징 시스템
- **예외 처리**: 통합된 전역 예외 처리로 일관된 API 응답
- **AOP**: 성능 로깅 및 권한 검사를 위한 관점 지향 프로그래밍 적용