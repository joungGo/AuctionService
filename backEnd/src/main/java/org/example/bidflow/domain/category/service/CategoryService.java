package org.example.bidflow.domain.category.service;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.category.dto.CategoryRequest;
import org.example.bidflow.domain.category.dto.CategoryResponse;
import org.example.bidflow.domain.category.entity.Category;
import org.example.bidflow.domain.category.repository.CategoryRepository;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream()
                .map(CategoryResponse::from)
                .collect(Collectors.toList());
    }

    public CategoryResponse getCategoryById(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
        return CategoryResponse.from(category);
    }

    public Category getCategoryEntityById(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("카테고리를 찾을 수 없습니다."));
    }

    // 관리자: 카테고리 생성
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        System.out.println("카테고리 생성 서비스 호출: " + request.getCategoryName());
        
        try {
            // 카테고리명 중복 검사
            if (categoryRepository.findByCategoryName(request.getCategoryName()).isPresent()) {
                System.out.println("카테고리명 중복: " + request.getCategoryName());
                throw new ServiceException("400", "이미 존재하는 카테고리명입니다.");
            }

            // 새로운 카테고리의 sortOrder 계산 (기존 최대값 + 1)
            Integer maxSortOrder = categoryRepository.findMaxSortOrder().orElse(0);
            Integer newSortOrder = maxSortOrder + 1;

            Category category = Category.builder()
                    .categoryName(request.getCategoryName())
                    .categoryCode(request.getCategoryName().toUpperCase().replaceAll("\\s+", "_"))
                    .description(null)
                    .imageUrl(null)
                    .sortOrder(newSortOrder)
                    .build();

            System.out.println("카테고리 엔티티 생성 완료: " + category.getCategoryName());
            
            Category savedCategory = categoryRepository.save(category);
            System.out.println("카테고리 저장 완료: " + savedCategory.getCategoryId());
            
            return CategoryResponse.from(savedCategory);
        } catch (Exception e) {
            System.err.println("카테고리 생성 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    // 관리자: 카테고리 수정
    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = getCategoryEntityById(categoryId);
        
        // 카테고리명 변경 시 중복 검사
        if (!category.getCategoryName().equals(request.getCategoryName()) &&
            categoryRepository.findByCategoryName(request.getCategoryName()).isPresent()) {
            throw new ServiceException("400", "이미 존재하는 카테고리명입니다.");
        }

        // 새로운 Category 객체 생성
        Category updatedCategory = Category.builder()
                .categoryId(category.getCategoryId())
                .categoryName(request.getCategoryName())
                .categoryCode(request.getCategoryName().toUpperCase().replaceAll("\\s+", "_"))
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .products(category.getProducts())
                .build();

        Category savedCategory = categoryRepository.save(updatedCategory);
        return CategoryResponse.from(savedCategory);
    }

    // 관리자: 카테고리 삭제
    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = getCategoryEntityById(categoryId);
        
        // 카테고리에 속한 상품이 있는지 확인
        if (!category.getProducts().isEmpty()) {
            throw new ServiceException("400", "해당 카테고리에 속한 상품이 있어 삭제할 수 없습니다.");
        }

        categoryRepository.delete(category);
    }
} 