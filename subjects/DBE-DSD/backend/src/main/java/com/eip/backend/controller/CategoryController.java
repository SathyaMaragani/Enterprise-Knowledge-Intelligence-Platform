package com.eip.backend.controller;

import com.eip.backend.dto.CategoryResponse;
import com.eip.backend.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /** Every category, by name. Category names are not document data, so any signed-in user may list them. */
    @GetMapping
    public List<CategoryResponse> getCategories() {
        return categoryService.getAllCategories().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName(), category.getDescription()))
                .sorted(Comparator.comparing(CategoryResponse::name))
                .toList();
    }
}
