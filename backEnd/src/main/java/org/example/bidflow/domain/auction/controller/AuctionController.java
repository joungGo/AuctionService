package org.example.bidflow.domain.auction.controller;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.auction.dto.AuctionCheckResponse;
import org.example.bidflow.domain.auction.dto.AuctionDetailResponse;
import org.example.bidflow.domain.auction.service.AuctionService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MessageMapping("/auction/participant/join")
    public void joinParticipant(@Payload Map<String, String> payload) {
        String auctionId = payload.get("auctionId");
        String userUUID = payload.get("userUUID");
        String key = "auction:" + auctionId + ":participants";
        redisTemplate.opsForSet().add(key, userUUID);
        broadcastParticipantCount(auctionId, key);
    }

    @MessageMapping("/auction/participant/leave")
    public void leaveParticipant(@Payload Map<String, String> payload) {
        String auctionId = payload.get("auctionId");
        String userUUID = payload.get("userUUID");
        String key = "auction:" + auctionId + ":participants";
        redisTemplate.opsForSet().remove(key, userUUID);
        broadcastParticipantCount(auctionId, key);
    }

    private void broadcastParticipantCount(String auctionId, String key) {
        Long count = redisTemplate.opsForSet().size(key);
        simpMessagingTemplate.convertAndSend("/sub/auction/" + auctionId,
                java.util.Collections.singletonMap("participantCount", count != null ? count : 0));
    }

    @GetMapping
    public ResponseEntity<RsData<List<AuctionCheckResponse>>> getAllAuctions() {
        // AuctionService에서 AuctionResponse 리스트를 반환
        List<AuctionCheckResponse> response = auctionService.getAllAuctions();
        RsData<List<AuctionCheckResponse>> rsData = new RsData<>("200", "전체 조회가 완료되었습니다.", response);
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
}