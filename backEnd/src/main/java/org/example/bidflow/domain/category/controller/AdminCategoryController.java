package org.example.bidflow.domain.category.controller;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.category.dto.CategoryRequest;
import org.example.bidflow.domain.category.dto.CategoryResponse;
import org.example.bidflow.domain.category.service.CategoryService;
import org.example.bidflow.domain.user.entity.Role;
import org.example.bidflow.global.annotation.HasRole;
import org.example.bidflow.global.controller.BaseController;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/categories")
public class AdminCategoryController extends BaseController {

    private final CategoryService categoryService;

    // 관리자: 카테고리 목록 조회
    @HasRole(Role.ADMIN)
    @GetMapping
    public ResponseEntity<RsData<List<CategoryResponse>>> getAllCategories() {
        long startTime = startOperation("getAllCategories", "카테고리 목록 조회");
        try {
            List<CategoryResponse> categories = categoryService.getAllCategories();
            endOperation("getAllCategories", "카테고리 목록 조회", startTime);
            return successResponse("카테고리 목록 조회가 완료되었습니다.", categories);
        } catch (Exception e) {
            endOperation("getAllCategories", "카테고리 목록 조회", startTime);
            throw e;
        }
    }

    // 관리자: 카테고리 상세 조회
    @HasRole(Role.ADMIN)
    @GetMapping("/{categoryId}")
    public ResponseEntity<RsData<CategoryResponse>> getCategoryById(@PathVariable Long categoryId) {
        long startTime = startOperation("getCategoryById", "카테고리 상세 조회");
        try {
            CategoryResponse category = categoryService.getCategoryById(categoryId);
            endOperation("getCategoryById", "카테고리 상세 조회", startTime);
            return successResponse("카테고리 조회가 완료되었습니다.", category);
        } catch (Exception e) {
            endOperation("getCategoryById", "카테고리 상세 조회", startTime);
            throw e;
        }
    }

    // 관리자: 카테고리 생성
    @HasRole(Role.ADMIN)
    @PostMapping
    public ResponseEntity<RsData<CategoryResponse>> createCategory(@RequestBody CategoryRequest request) {
        long startTime = startOperation("createCategory", "카테고리 생성");
        try {
            CategoryResponse category = categoryService.createCategory(request);
            endOperation("createCategory", "카테고리 생성", startTime);
            return createdResponse("카테고리가 성공적으로 생성되었습니다.", category);
        } catch (Exception e) {
            endOperation("createCategory", "카테고리 생성", startTime);
            throw e;
        }
    }

    // 관리자: 카테고리 수정
    @HasRole(Role.ADMIN)
    @PutMapping("/{categoryId}")
    public ResponseEntity<RsData<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody CategoryRequest request) {
        long startTime = startOperation("updateCategory", "카테고리 수정");
        try {
            CategoryResponse category = categoryService.updateCategory(categoryId, request);
            endOperation("updateCategory", "카테고리 수정", startTime);
            return successResponse("카테고리가 성공적으로 수정되었습니다.", category);
        } catch (Exception e) {
            endOperation("updateCategory", "카테고리 수정", startTime);
            throw e;
        }
    }

    // 관리자: 카테고리 삭제
    @HasRole(Role.ADMIN)
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<RsData<Void>> deleteCategory(@PathVariable Long categoryId) {
        long startTime = startOperation("deleteCategory", "카테고리 삭제");
        try {
            categoryService.deleteCategory(categoryId);
            endOperation("deleteCategory", "카테고리 삭제", startTime);
            return successResponse("카테고리가 성공적으로 삭제되었습니다.");
        } catch (Exception e) {
            endOperation("deleteCategory", "카테고리 삭제", startTime);
            throw e;
        }
    }
} 