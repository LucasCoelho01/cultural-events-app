package com.culturalevents.categories.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.culturalevents.categories.application.CategoriesService;
import com.culturalevents.shared.api.CategoriesApi;
import com.culturalevents.shared.api.model.Category;

@RestController
class CategoriesController implements CategoriesApi {

    private final CategoriesService service;

    CategoriesController(CategoriesService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Category>> listCategories() {
        return ResponseEntity.ok(service.listTree().stream().map(CategoriesMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<Category> getCategory(String slug) {
        return ResponseEntity.ok(CategoriesMapper.toDto(service.getBySlug(slug)));
    }
}
