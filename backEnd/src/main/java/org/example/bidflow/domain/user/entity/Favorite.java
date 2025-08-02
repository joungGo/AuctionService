package org.example.bidflow.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.bidflow.domain.auction.entity.Auction;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "FAVORITE_TABLE", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"USER_UUID", "AUCTION_ID"})
})
public class Favorite {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAVORITE_ID")
    private Long favoriteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_UUID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "AUCTION_ID", nullable = false)
    private Auction auction;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
} 