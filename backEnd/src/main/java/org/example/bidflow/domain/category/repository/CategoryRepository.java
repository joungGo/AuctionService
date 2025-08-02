package org.example.bidflow.domain.category.repository;

import org.example.bidflow.domain.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryName(String categoryName);
    
    @Query("SELECT MAX(c.sortOrder) FROM Category c")
    Optional<Integer> findMaxSortOrder();
} 