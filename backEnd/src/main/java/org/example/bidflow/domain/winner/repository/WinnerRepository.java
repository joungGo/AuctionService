package org.example.bidflow.domain.winner.repository;

import org.example.bidflow.domain.winner.entity.Winner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WinnerRepository extends JpaRepository<Winner, Long> {
    // 기존 메서드 (N+1 문제 발생)
    List<Winner> findByUser_UserUUID(String userUUID);
    
    // 새로운 메서드 (N+1 문제 해결)
    @Query("SELECT w FROM Winner w " +
           "JOIN FETCH w.auction a " +
           "JOIN FETCH a.product p " +
           "WHERE w.user.userUUID = :userUUID")
    List<Winner> findByUserUUIDWithAuctionAndProduct(@Param("userUUID") String userUUID);
}
