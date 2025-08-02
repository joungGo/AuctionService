package org.example.bidflow.domain.product.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.bidflow.domain.product.entity.Product;
import org.example.bidflow.domain.category.entity.Category;

@Getter
@Builder
public class ProductResponse {

    private final Long productId;
    private final String productName;
    private final String imageUrl;
    private final String description;
    private final Long categoryId;
    private final String categoryName;

    // 엔티티를 DTO로 변환
    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return ProductResponse.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .imageUrl(product.getImageUrl())
                .description(product.getDescription())
                .categoryId(category != null ? category.getCategoryId() : null)
                .categoryName(category != null ? category.getCategoryName() : null)
                .build();
    }

}

