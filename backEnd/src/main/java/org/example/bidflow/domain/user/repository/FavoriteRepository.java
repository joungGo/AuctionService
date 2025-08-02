package org.example.bidflow.domain.user.repository;

import org.example.bidflow.domain.user.entity.Favorite;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.auction.entity.Auction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUser(User user);
    Optional<Favorite> findByUserAndAuction(User user, Auction auction);
    void deleteByUserAndAuction(User user, Auction auction);
} 