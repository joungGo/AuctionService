package org.example.bidflow.domain.bid.repository;

import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    // 최고 입찰자(최고가 입찰) 조회
    Bid findTopByAuctionOrderByAmountDesc(Auction auction);
    
    // 특정 경매의 모든 입찰 내역 조회 (최신순)
    List<Bid> findByAuctionOrderByBidTimeDesc(Auction auction);
    
    // 특정 경매의 입찰 내역 페이징 조회 (최신순)
    Page<Bid> findByAuctionOrderByBidTimeDesc(Auction auction, Pageable pageable);
    
    // 특정 사용자의 모든 입찰 내역 조회
    List<Bid> findByUserOrderByBidTimeDesc(User user);
    
    // 특정 경매에서 특정 사용자의 입찰 내역 조회
    List<Bid> findByAuctionAndUserOrderByBidTimeDesc(Auction auction, User user);
    
    // 특정 경매의 입찰 개수 조회
    long countByAuction(Auction auction);
    
    // 특정 경매의 최고 입찰가 조회 (JPQL)
    @Query("SELECT MAX(b.amount) FROM Bid b WHERE b.auction = :auction")
    Optional<Integer> findMaxAmountByAuction(@Param("auction") Auction auction);

    // 사용자가 입찰한 경매 ID 목록(distinct) 조회
    @Query("SELECT DISTINCT b.auction.auctionId FROM Bid b WHERE b.user = :user")
    List<Long> findDistinctAuctionIdsByUser(@Param("user") User user);

    // 오늘의 총 입찰 수 조회
    @Query("SELECT COUNT(b) FROM Bid b WHERE DATE(b.bidTime) = CURRENT_DATE")
    Long countTodayBids();

    // 경매별 평균 입찰가와 최고가 비율 조회
    @Query("SELECT AVG(b.amount), MAX(b.amount) FROM Bid b WHERE b.auction IN " +
           "(SELECT a FROM Auction a WHERE a.status = 'ONGOING')")
    List<Object[]> findAvgAndMaxBidAmountsForOngoingAuctions();
}
