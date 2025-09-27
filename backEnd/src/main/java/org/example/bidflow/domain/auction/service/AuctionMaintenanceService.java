package org.example.bidflow.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.category.entity.Category;
import org.example.bidflow.domain.category.repository.CategoryRepository;
import org.example.bidflow.domain.product.entity.Product;
import org.example.bidflow.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 경매 유지보수 관련 비즈니스 로직을 담당하는 서비스
 * 
 * 이 서비스는 경매 시스템의 유지보수 작업을 담당합니다.
 * 기본 카테고리 할당, 데이터 정리 등의 작업을 수행합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionMaintenanceService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * 기존 경매들에 기본 카테고리를 할당합니다. (한 번만 실행)
     */
    @Transactional
    public void assignDefaultCategoryToExistingAuctions() {
        // 기본 카테고리 조회 (첫 번째 카테고리 사용)
        List<Category> categories = categoryRepository.findAll();
        if (categories.isEmpty()) {
            log.warn("카테고리가 없어서 기본 카테고리 할당을 건너뜁니다.");
            return;
        }
        
        Category defaultCategory = categories.get(0);
        
        // 카테고리가 없는 상품들 조회
        List<Product> productsWithoutCategory = productRepository.findAll().stream()
                .filter(product -> product.getCategory() == null)
                .collect(Collectors.toList());
        
        for (Product product : productsWithoutCategory) {
            Product updatedProduct = Product.builder()
                    .productId(product.getProductId())
                    .productName(product.getProductName())
                    .imageUrl(product.getImageUrl())
                    .description(product.getDescription())
                    .category(defaultCategory)
                    .auction(product.getAuction())
                    .build();
            productRepository.save(updatedProduct);
            log.info("상품 '{}'에 기본 카테고리 '{}' 할당", product.getProductName(), defaultCategory.getCategoryName());
        }
        
        log.info("총 {}개 상품에 기본 카테고리 할당 완료", productsWithoutCategory.size());
    }
}
