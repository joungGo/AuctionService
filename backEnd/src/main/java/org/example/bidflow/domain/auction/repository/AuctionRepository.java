package org.example.bidflow.domain.auction.repository;

import org.example.bidflow.data.AuctionStatus;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuctionRepository extends JpaRepository<Auction,Long> {

    // 사용자 - 전체 경매 상품 리스트 조회하는 쿼리
    @Query("SELECT a FROM Auction a JOIN FETCH a.product p LEFT JOIN FETCH p.category")
    List<Auction> findAllAuctions();

    // 카테고리별 경매 상품 리스트 조회하는 쿼리
    @Query("SELECT a FROM Auction a JOIN FETCH a.product p LEFT JOIN FETCH p.category WHERE p.category = :category")
    List<Auction> findAllAuctionsByCategory(Category category);

    // 관리자 - 전체 경매 상품 리스트 조회하는 쿼리
    @Query("SELECT a FROM Auction a JOIN FETCH a.product p LEFT JOIN FETCH p.category LEFT JOIN FETCH a.winner")
    List<Auction> findAllAuctionsWithProductAndWinner();

    @Query("SELECT a FROM Auction a JOIN FETCH a.product p LEFT JOIN FETCH p.category WHERE a.auctionId = :auctionId")
    Optional<Auction> findByAuctionId(Long auctionId);

    // 스케줄 복구를 위해 필요한 경매들 조회 (UPCOMING 또는 ONGOING 상태)
    @Query("SELECT a FROM Auction a JOIN FETCH a.product p LEFT JOIN FETCH p.category " +
           "WHERE a.status IN ('UPCOMING', 'ONGOING') " +
           "AND (a.startTime > :now OR a.endTime > :now)")
    List<Auction> findAuctionsNeedingSchedule(LocalDateTime now);
}



