package org.example.bidflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * HikariCP 커넥션 풀 부하 테스트 클래스
 * 
 * 목적: 
 * - 다중 사용자 동시 요청을 시뮬레이션하여 HikariCP 커넥션 풀의 동작을 관찰
 * - Grafana에서 커넥션 풀 메트릭 변화를 실시간으로 모니터링
 * - 커넥션 풀의 성능과 한계를 테스트
 * 
 * 테스트 종류:
 * 1. 동시 요청 테스트: 여러 요청을 동시에 보내 커넥션 풀 사용량 증가
 * 2. 지속적 부하 테스트: 일정 시간 동안 지속적으로 요청을 보내 안정성 테스트
 * 3. 사용자 시나리오 테스트: 실제 사용자 행동을 시뮬레이션한 복합 테스트
 * 
 * ⚠️ 중요: 이 테스트는 이미 실행 중인 Spring Boot 애플리케이션(8080 포트)에 
 * HTTP 요청을 보내는 방식입니다. 애플리케이션을 중지하지 마세요!
 */
public class ConnectionPoolLoadTest {

    // 테스트 대상 Spring Boot 애플리케이션의 기본 URL (실행 중인 애플리케이션)
    private static final String BASE_URL = "http://localhost:8080";
    
    // HTTP 요청을 위한 클라이언트 설정
    // - connectTimeout: 연결 타임아웃 10초
    // - 재사용 가능한 클라이언트로 성능 최적화
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 기본 동시 요청 테스트
     * 
     * 목적: 
     * - 30개의 요청을 동시에 보내 커넥션 풀 사용량을 급격히 증가시킴
     * - HikariCP의 동시 커넥션 처리 능력을 테스트
     * - Grafana에서 hikaricp_connections_active 메트릭 변화 관찰
     * 
     * 예상 결과:
     * - 활성 커넥션 수가 증가 (0 → 10~30개)
     * - 커넥션 풀 사용률 증가 (0% → 30~100%)
     * - 대기 중인 스레드 수 증가 가능성
     */
    @Test
    public void testConcurrentRequests() throws Exception {
        System.out.println("🚀 동시 요청 테스트 시작...");
        System.out.println("📊 Grafana에서 hikaricp_connections_active 메트릭을 관찰하세요!");
        System.out.println("⚠️  기존 Spring Boot 애플리케이션이 8080 포트에서 실행 중인지 확인하세요!");
        
        // 동시 요청 수 설정 (커넥션 풀 크기보다 큰 값으로 설정)
        int numRequests = 30;
        
        // 고정 크기 스레드 풀 생성 (요청 수만큼 스레드 생성)
        // - 각 스레드가 하나의 HTTP 요청을 담당
        // - 동시에 실행되어 커넥션 풀에 부하 생성
        ExecutorService executor = Executors.newFixedThreadPool(numRequests);
        
        // CompletableFuture 배열로 비동기 작업 관리
        // - 각 요청을 비동기적으로 실행
        // - 모든 요청이 완료될 때까지 대기
        CompletableFuture<Void>[] futures = new CompletableFuture[numRequests];
        
        // 각 요청을 비동기적으로 실행
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    // 경매 목록 조회 API 호출 (데이터베이스 쿼리 발생)
                    makeRequest("/api/auctions", requestId);
                } catch (Exception e) {
                    System.err.println("요청 " + requestId + " 실패: " + e.getMessage());
                }
            }, executor);
        }
        
        // 모든 비동기 작업이 완료될 때까지 대기
        CompletableFuture.allOf(futures).join();
        
        // 스레드 풀 종료 및 정리
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        System.out.println("✅ 동시 요청 테스트 완료!");
        System.out.println("📈 Grafana에서 커넥션 풀 메트릭 변화를 확인하세요!");
    }

    /**
     * 지속적인 부하 테스트
     * 
     * 목적:
     * - 20명의 사용자가 60초간 지속적으로 요청을 보냄
     * - 커넥션 풀의 장기간 안정성과 성능 테스트
     * - 커넥션 누수나 메모리 문제 감지
     * 
     * 테스트 시나리오:
     * - 각 사용자는 4가지 다른 엔드포인트를 순환하며 요청
     * - 요청 간 랜덤한 지연 시간 (0.1~2초)
     * - 60초 동안 지속적인 부하 생성
     */
    @Test
    public void testContinuousLoad() throws Exception {
        System.out.println("🔥 지속적 부하 테스트 시작...");
        System.out.println("⏱️ 60초간 지속적인 부하를 생성합니다...");
        System.out.println("⚠️  기존 Spring Boot 애플리케이션이 8080 포트에서 실행 중인지 확인하세요!");
        
        // 테스트 설정
        int numUsers = 20;        // 동시 사용자 수
        int durationSeconds = 60; // 테스트 지속 시간 (초)
        
        // 사용자 수만큼 스레드 풀 생성
        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        
        // 각 사용자별 비동기 작업 생성
        CompletableFuture<Void>[] futures = new CompletableFuture[numUsers];
        for (int i = 0; i < numUsers; i++) {
            final int userId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                // 각 사용자가 지속적으로 요청을 보내는 메서드 호출
                continuousRequests(userId, durationSeconds);
            }, executor);
        }
        
        // 모든 사용자의 작업 완료 대기
        CompletableFuture.allOf(futures).join();
        
        // 리소스 정리
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        System.out.println("✅ 지속적 부하 테스트 완료!");
        System.out.println("📊 장기간 부하에 대한 커넥션 풀 안정성을 확인하세요!");
    }

    /**
     * 사용자 시나리오 테스트
     * 
     * 목적:
     * - 실제 사용자의 행동 패턴을 시뮬레이션
     * - 복합적인 데이터베이스 작업으로 커넥션 풀 부하 생성
     * - 다양한 API 엔드포인트 조합 테스트
     * 
     * 사용자 여정:
     * 1. 경매 목록 조회 → 2. 특정 경매 상세 조회 → 3. 카테고리 검색 → 4. 복잡한 검색
     * 각 단계마다 랜덤한 지연 시간으로 현실적인 사용자 행동 시뮬레이션
     */
    @Test
    public void testUserScenarios() throws Exception {
        System.out.println("👥 사용자 시나리오 테스트 시작...");
        System.out.println("🎭 실제 사용자 행동을 시뮬레이션합니다...");
        System.out.println("⚠️  기존 Spring Boot 애플리케이션이 8080 포트에서 실행 중인지 확인하세요!");
        
        // 동시 사용자 수 설정
        int numUsers = 25;
        
        // 사용자별 스레드 풀 생성
        ExecutorService executor = Executors.newFixedThreadPool(numUsers);
        
        // 각 사용자별 시나리오 실행
        CompletableFuture<Void>[] futures = new CompletableFuture[numUsers];
        for (int i = 0; i < numUsers; i++) {
            final int userId = i;
            futures[i] = CompletableFuture.runAsync(() -> {
                // 사용자별 여정 시뮬레이션
                simulateUserJourney(userId);
            }, executor);
        }
        
        // 모든 사용자 시나리오 완료 대기
        CompletableFuture.allOf(futures).join();
        
        // 리소스 정리
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        System.out.println("✅ 사용자 시나리오 테스트 완료!");
        System.out.println("🎯 복합적인 사용자 행동에 대한 커넥션 풀 반응을 확인하세요!");
    }

    /**
     * 개선된 동시 요청 테스트 (지속적인 부하 생성)
     * 
     * 목적:
     * - 더 오래 지속되는 부하를 생성하여 Active Connections 관찰
     * - 각 요청에 인위적인 지연을 추가하여 커넥션 사용 시간 연장
     * - 다양한 API 엔드포인트를 사용하여 복합적인 부하 생성
     * 
     * 예상 결과:
     * - Active Connections이 0보다 큰 값으로 증가
     * - 커넥션 풀 사용률 증가
     * - 더 명확한 메트릭 변화 관찰 가능
     */
    @Test
    public void testSustainedConcurrentRequests() throws Exception {
        System.out.println("🔥 개선된 동시 요청 테스트 시작...");
        System.out.println("📊 Grafana에서 hikaricp_connections_active 메트릭을 관찰하세요!");
        System.out.println("⚠️  기존 Spring Boot 애플리케이션이 8080 포트에서 실행 중인지 확인하세요!");
        
        // 동시 요청 수 설정 (커넥션 풀 크기보다 큰 값으로 설정)
        int numRequests = 50;  // 더 많은 요청
        
        // 고정 크기 스레드 풀 생성
        ExecutorService executor = Executors.newFixedThreadPool(numRequests);
        
        // CompletableFuture 배열로 비동기 작업 관리
        CompletableFuture<Void>[] futures = new CompletableFuture[numRequests];
        
        // 테스트할 API 엔드포인트 목록 (다양한 복잡도)
        String[] endpoints = {
            "/api/auctions",                    // 경매 목록 조회
            "/api/auctions/1",                  // 특정 경매 상세 조회
            "/api/categories",                  // 카테고리 목록 조회
            "/api/auctions?status=ONGOING",     // 진행 중인 경매 검색
            "/api/auctions?category=1",         // 카테고리별 검색
            "/api/auctions?page=0&size=20"      // 페이징 처리
        };
        
        // 각 요청을 비동기적으로 실행
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            final String endpoint = endpoints[i % endpoints.length];  // 엔드포인트 순환
            
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    // 지속적인 부하를 위한 반복 요청
                    for (int j = 0; j < 3; j++) {  // 각 스레드가 3번씩 요청
                        makeRequestWithDelay(endpoint, requestId, j);
                        
                        // 요청 간 짧은 지연 (0.5~2초)
                        Thread.sleep(500 + (long)(Math.random() * 1500));
                    }
                } catch (Exception e) {
                    System.err.println("요청 " + requestId + " 실패: " + e.getMessage());
                }
            }, executor);
        }
        
        // 모든 비동기 작업이 완료될 때까지 대기
        CompletableFuture.allOf(futures).join();
        
        // 스레드 풀 종료 및 정리
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        System.out.println("✅ 개선된 동시 요청 테스트 완료!");
        System.out.println("📈 Grafana에서 커넥션 풀 메트릭 변화를 확인하세요!");
    }

    /**
     * 단일 HTTP 요청 수행 메서드
     * 
     * @param endpoint API 엔드포인트 경로 (예: "/api/auctions")
     * @param requestId 요청 식별자 (로그 출력용)
     * 
     * 동작 과정:
     * 1. HTTP GET 요청 생성
     * 2. 10초 타임아웃 설정
     * 3. 요청 전송 및 응답 수신
     * 4. HTTP 상태 코드 로그 출력
     * 
     * 데이터베이스 영향:
     * - 각 요청은 데이터베이스 쿼리를 발생시킴
     * - HikariCP에서 커넥션을 획득하고 해제하는 과정 발생
     * - Prometheus가 이 과정을 메트릭으로 수집
     */
    private void makeRequest(String endpoint, int requestId) throws IOException, InterruptedException {
        // HTTP GET 요청 생성
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(10))  // 요청 타임아웃 10초
                .GET()
                .build();

        // 요청 전송 및 응답 수신
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // 요청 결과 로그 출력
        System.out.println("요청 " + requestId + ": " + response.statusCode());
    }

    /**
     * 지연이 포함된 HTTP 요청 수행 메서드
     * 
     * @param endpoint API 엔드포인트 경로
     * @param requestId 요청 식별자
     * @param attempt 시도 횟수
     * 
     * 특징:
     * - 요청 전후에 인위적인 지연 추가
     * - 커넥션 사용 시간을 연장하여 Active Connections 관찰 가능
     * - 더 현실적인 부하 시뮬레이션
     */
    private void makeRequestWithDelay(String endpoint, int requestId, int attempt) throws IOException, InterruptedException {
        try {
            // 요청 전 짧은 지연 (0.1~0.5초)
            Thread.sleep(100 + (long)(Math.random() * 400));
            
            // HTTP GET 요청 생성
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + endpoint))
                    .timeout(Duration.ofSeconds(15))  // 타임아웃 증가
                    .GET()
                    .build();

            // 요청 전송 및 응답 수신
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 요청 후 지연 (0.5~1.5초) - 커넥션 사용 시간 연장
            Thread.sleep(500 + (long)(Math.random() * 1000));
            
            // 요청 결과 로그 출력
            System.out.println("요청 " + requestId + "-" + attempt + ": " + response.statusCode() + " (" + endpoint + ")");
            
        } catch (Exception e) {
            System.err.println("요청 " + requestId + "-" + attempt + " 실패: " + e.getMessage());
        }
    }

    /**
     * 지속적인 요청 수행 메서드
     * 
     * @param userId 사용자 식별자
     * @param durationSeconds 테스트 지속 시간 (초)
     * 
     * 동작 방식:
     * 1. 지정된 시간 동안 반복적으로 요청 전송
     * 2. 4가지 엔드포인트를 순환하며 요청
     * 3. 요청 간 랜덤한 지연 시간 (0.1~2초)
     * 4. 오류 발생 시 1초 대기 후 재시도
     * 
     * 엔드포인트 설명:
     * - /api/auctions: 경매 목록 조회 (단순 SELECT)
     * - /api/auctions/1: 특정 경매 상세 조회 (WHERE 조건)
     * - /api/categories: 카테고리 목록 조회 (JOIN 가능성)
     * - /api/auctions?status=ONGOING: 조건부 검색 (복합 쿼리)
     */
    private void continuousRequests(int userId, int durationSeconds) {
        // 테스트 종료 시간 계산
        long endTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        
        // 테스트할 API 엔드포인트 목록
        String[] endpoints = {
                "/api/auctions",                    // 경매 목록 조회
                "/api/auctions/1",                  // 특정 경매 상세 조회
                "/api/categories",                  // 카테고리 목록 조회
                "/api/auctions?status=ONGOING"      // 진행 중인 경매 검색
        };
        
        int requestCount = 0;
        
        // 지정된 시간까지 반복 요청
        while (System.currentTimeMillis() < endTime) {
            try {
                // 엔드포인트 순환 선택 (모듈로 연산으로 4개 엔드포인트 순환)
                String endpoint = endpoints[requestCount % endpoints.length];
                
                // HTTP 요청 전송
                makeRequest(endpoint, userId * 1000 + requestCount);
                requestCount++;
                
                // 랜덤한 지연 시간 (0.1초 ~ 2초)
                // - 현실적인 사용자 행동 시뮬레이션
                // - 커넥션 풀에 지속적인 부하 생성
                Thread.sleep(100 + (long)(Math.random() * 1900));
                
            } catch (Exception e) {
                // 오류 발생 시 로그 출력
                System.err.println("사용자 " + userId + " 오류: " + e.getMessage());
                try {
                    // 오류 후 1초 대기 후 재시도
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // 사용자별 완료 요약 출력
        System.out.println("사용자 " + userId + " 완료: " + requestCount + "개 요청");
    }

    /**
     * 사용자 여정 시뮬레이션 메서드
     * 
     * @param userId 사용자 식별자
     * 
     * 시뮬레이션 시나리오:
     * 1. 경매 목록 조회 (0.5~2초 대기)
     * 2. 특정 경매 상세 조회 (1~3초 대기)
     * 3. 카테고리별 검색 (0.5~1.5초 대기)
     * 4. 복잡한 검색 조건 (2~5초 대기)
     * 
     * 각 단계는 실제 사용자의 행동 패턴을 반영:
     * - 페이지 로딩 시간
     * - 사용자 읽기 시간
     * - 다음 액션 결정 시간
     * 
     * 데이터베이스 영향:
     * - 다양한 복잡도의 쿼리 실행
     * - 커넥션 풀 사용량 변화
     * - Grafana에서 메트릭 변화 관찰 가능
     */
    private void simulateUserJourney(int userId) {
        try {
            // 1단계: 경매 목록 조회 (메인 페이지)
            System.out.println("사용자 " + userId + ": 경매 목록 조회");
            makeRequest("/api/auctions", userId);
            
            // 사용자가 페이지를 읽는 시간 시뮬레이션 (0.5~2초)
            Thread.sleep(500 + (long)(Math.random() * 1500));
            
            // 2단계: 특정 경매 상세 조회 (상세 페이지)
            System.out.println("사용자 " + userId + ": 경매 상세 조회");
            makeRequest("/api/auctions/1", userId);
            
            // 상세 정보를 읽는 시간 시뮬레이션 (1~3초)
            Thread.sleep(1000 + (long)(Math.random() * 2000));
            
            // 3단계: 카테고리별 검색 (필터링)
            System.out.println("사용자 " + userId + ": 카테고리 검색");
            makeRequest("/api/categories", userId);
            
            // 카테고리 선택 시간 시뮬레이션 (0.5~1.5초)
            Thread.sleep(500 + (long)(Math.random() * 1000));
            
            // 4단계: 복잡한 검색 조건 (고급 검색)
            System.out.println("사용자 " + userId + ": 복잡한 검색");
            makeRequest("/api/auctions?status=ONGOING&category=1", userId);
            
            // 검색 결과 분석 시간 시뮬레이션 (2~5초)
            Thread.sleep(2000 + (long)(Math.random() * 3000));
            
        } catch (Exception e) {
            // 사용자 시나리오 실행 중 오류 발생 시 로그 출력
            System.err.println("사용자 " + userId + " 시나리오 오류: " + e.getMessage());
        }
    }
}