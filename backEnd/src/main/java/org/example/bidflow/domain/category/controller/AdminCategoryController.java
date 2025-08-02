package org.example.bidflow.domain.category.controller;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.data.Role;
import org.example.bidflow.domain.category.dto.CategoryRequest;
import org.example.bidflow.domain.category.dto.CategoryResponse;
import org.example.bidflow.domain.category.service.CategoryService;
import org.example.bidflow.global.annotation.HasRole;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    // 관리자: 카테고리 목록 조회
    @HasRole(Role.ADMIN)
    @GetMapping
    public ResponseEntity<RsData<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        RsData<List<CategoryResponse>> rsData = new RsData<>("200", "카테고리 목록 조회가 완료되었습니다.", categories);
        return ResponseEntity.ok(rsData);
    }

    // 관리자: 카테고리 상세 조회
    @HasRole(Role.ADMIN)
    @GetMapping("/{categoryId}")
    public ResponseEntity<RsData<CategoryResponse>> getCategoryById(@PathVariable Long categoryId) {
        CategoryResponse category = categoryService.getCategoryById(categoryId);
        RsData<CategoryResponse> rsData = new RsData<>("200", "카테고리 조회가 완료되었습니다.", category);
        return ResponseEntity.ok(rsData);
    }

    // 관리자: 카테고리 생성
    @HasRole(Role.ADMIN)
    @PostMapping
    public ResponseEntity<RsData<CategoryResponse>> createCategory(@RequestBody CategoryRequest request) {
        System.out.println("카테고리 생성 요청: " + request.getCategoryName());
        CategoryResponse category = categoryService.createCategory(request);
        RsData<CategoryResponse> rsData = new RsData<>("201", "카테고리가 성공적으로 생성되었습니다.", category);
        return ResponseEntity.ok(rsData);
    }

    // 관리자: 카테고리 수정
    @HasRole(Role.ADMIN)
    @PutMapping("/{categoryId}")
    public ResponseEntity<RsData<CategoryResponse>> updateCategory(
            @PathVariable Long categoryId,
            @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.updateCategory(categoryId, request);
        RsData<CategoryResponse> rsData = new RsData<>("200", "카테고리가 성공적으로 수정되었습니다.", category);
        return ResponseEntity.ok(rsData);
    }

    // 관리자: 카테고리 삭제
    @HasRole(Role.ADMIN)
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<RsData<Void>> deleteCategory(@PathVariable Long categoryId) {
        categoryService.deleteCategory(categoryId);
        RsData<Void> rsData = new RsData<>("200", "카테고리가 성공적으로 삭제되었습니다.", null);
        return ResponseEntity.ok(rsData);
    }
} 