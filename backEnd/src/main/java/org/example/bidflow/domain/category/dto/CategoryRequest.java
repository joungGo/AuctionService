package org.example.bidflow.domain.category.dto;

import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {
    private String categoryName;
    private String description;
    private String imageUrl;
} 