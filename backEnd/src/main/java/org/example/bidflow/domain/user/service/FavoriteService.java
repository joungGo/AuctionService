package org.example.bidflow.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.domain.user.entity.Favorite;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.user.repository.FavoriteRepository;
import org.example.bidflow.domain.user.repository.UserRepository;
import org.example.bidflow.domain.user.dto.FavoriteResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;

    @Transactional
    public Favorite addFavorite(String userUUID, Long auctionId) {
        User user = userRepository.findByUserUUID(userUUID)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다."));
        // 이미 찜한 경우 예외
        if (favoriteRepository.findByUserAndAuction(user, auction).isPresent()) {
            throw new IllegalStateException("이미 찜한 경매입니다.");
        }
        Favorite favorite = Favorite.builder()
                .user(user)
                .auction(auction)
                .build();
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(String userUUID, Long auctionId) {
        User user = userRepository.findByUserUUID(userUUID)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다."));
        favoriteRepository.deleteByUserAndAuction(user, auction);
    }

    @Transactional(readOnly = true)
    public List<Favorite> getFavorites(String userUUID) {
        User user = userRepository.findByUserUUID(userUUID)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return favoriteRepository.findByUser(user);
    }

    public static FavoriteResponse toResponse(Favorite favorite) {
        return FavoriteResponse.builder()
                .favoriteId(favorite.getFavoriteId())
                .auctionId(favorite.getAuction().getAuctionId())
                .productName(favorite.getAuction().getProduct().getProductName())
                .imageUrl(favorite.getAuction().getProduct().getImageUrl())
                .createdAt(favorite.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavoriteResponses(String userUUID) {
        return getFavorites(userUUID).stream()
                .map(FavoriteService::toResponse)
                .toList();
    }
} 