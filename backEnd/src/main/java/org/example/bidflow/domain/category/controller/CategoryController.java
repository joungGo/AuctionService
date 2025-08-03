package org.example.bidflow.domain.category.controller;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.category.dto.CategoryResponse;
import org.example.bidflow.domain.category.service.CategoryService;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<RsData<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        RsData<List<CategoryResponse>> rsData = new RsData<>("200", "카테고리 목록 조회가 완료되었습니다.", categories);
        return ResponseEntity.ok(rsData);
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<RsData<CategoryResponse>> getCategoryById(@PathVariable Long categoryId) {
        CategoryResponse category = categoryService.getCategoryById(categoryId);
        RsData<CategoryResponse> rsData = new RsData<>("200", "카테고리 조회가 완료되었습니다.", category);
        return ResponseEntity.ok(rsData);
    }
} 