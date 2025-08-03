package org.example.bidflow.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.category.entity.Category;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "PRODUCT_TABLE")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PRODUCT_ID")
    private Long productId;

    @Column(name = "PRODUCT_NAME", nullable = false)
    private String productName;

    @Column(name = "IMAGE_URL")
    private String imageUrl;

    @Column(name = "DESCRIPTION")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATEGORY_ID")
    private Category category;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL)
    private Auction auction;
}
