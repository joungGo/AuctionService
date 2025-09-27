package org.example.bidflow.domain.auction.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionCheckResponse;
import org.example.bidflow.domain.auction.dto.AuctionDetailResponse;
import org.example.bidflow.domain.auction.dto.AuctionBidDetailResponse;
import org.example.bidflow.domain.auction.service.AuctionService;
import org.example.bidflow.global.controller.BaseController;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Set;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/auctions")
public class AuctionController extends BaseController {

    private final AuctionService auctionService;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MessageMapping("/auction/participant/join")
    public void joinParticipant(@Payload Map<String, String> payload) {
        String auctionId = payload.get("auctionId");
        String userUUID = payload.get("userUUID");
        log.info("👥 [참여자] JOIN 요청 - auctionId: {}, userUUID: {}", auctionId, userUUID);
        String key = "auction:" + auctionId + ":participant:" + userUUID;
        redisTemplate.opsForValue().set(key, "true", 2, TimeUnit.MINUTES);
        broadcastParticipantCount(auctionId);
    }

    @MessageMapping("/auction/participant/ping")
    public void pingParticipant(@Payload Map<String, String> payload) {
        String auctionId = payload.get("auctionId");
        String userUUID = payload.get("userUUID");
        log.info("👥 [참여자] PING 요청 - auctionId: {}, userUUID: {}", auctionId, userUUID);
        String key = "auction:" + auctionId + ":participant:" + userUUID;
        redisTemplate.opsForValue().set(key, "true", 2, TimeUnit.MINUTES);
        broadcastParticipantCount(auctionId);
    }

    @MessageMapping("/auction/participant/leave")
    public void leaveParticipant(@Payload Map<String, String> payload) {
        String auctionId = payload.get("auctionId");
        String userUUID = payload.get("userUUID");
        log.info("👥 [참여자] LEAVE 요청 - auctionId: {}, userUUID: {}", auctionId, userUUID);
        String key = "auction:" + auctionId + ":participant:" + userUUID;
        redisTemplate.delete(key);
        broadcastParticipantCount(auctionId);
    }

    private void broadcastParticipantCount(String auctionId) {
        // SCAN or KEYS to count participants
        Set<String> keys = redisTemplate.keys("auction:" + auctionId + ":participant:*");
        int count = (keys != null) ? keys.size() : 0;
        log.info("📢 [브로드캐스트] 참여자 수 전송 - auctionId: {}, count: {}", auctionId, count);
        simpMessagingTemplate.convertAndSend("/sub/auction/" + auctionId,
                java.util.Collections.singletonMap("participantCount", count));
    }

    @GetMapping
    public ResponseEntity<RsData<List<AuctionCheckResponse>>> getAllAuctions(
            @RequestParam(required = false) Long categoryId) {
        // AuctionService에서 AuctionResponse 리스트를 반환
        List<AuctionCheckResponse> response = auctionService.getAllAuctionsByCategory(categoryId);
        String message = categoryId != null ? "카테고리별 조회가 완료되었습니다." : "전체 조회가 완료되었습니다.";
        RsData<List<AuctionCheckResponse>> rsData = new RsData<>("200", message, response);
        return ResponseEntity.ok(rsData);
    }


    // Explain: FE 에서 FINISHED 상태로 변경시 요청할 엔드포인트 경로
    @PostMapping("/{auctionId}/close")
    public void  closeAuction(@PathVariable Long auctionId) {
        auctionService.closeAuction(auctionId);
    }

    // 특정 경매 상세 조회 컨트롤러
    @GetMapping("/{auctionId}")
    public ResponseEntity<RsData<AuctionDetailResponse>> getAuctionDetail(@PathVariable("auctionId") Long auctionId) {
        AuctionDetailResponse response = auctionService.getAuctionDetail(auctionId);
        RsData<AuctionDetailResponse> rsData = new RsData<>("200", "경매가 성공적으로 조회되었습니다.", response);
        return ResponseEntity.ok(rsData);
    }

    // 입찰 페이지 전용 상세 정보 API
    @GetMapping("/{auctionId}/bid-detail")
    public ResponseEntity<AuctionBidDetailResponse> getAuctionBidDetail(@PathVariable Long auctionId) {
        AuctionBidDetailResponse response = auctionService.getAuctionBidDetail(auctionId);
        return ResponseEntity.ok(response);
    }
}