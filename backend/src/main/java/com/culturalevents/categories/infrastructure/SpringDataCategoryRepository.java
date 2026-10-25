package com.culturalevents.categories.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.culturalevents.categories.domain.Category;

interface SpringDataCategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderBySortOrderAscNameAscIdAsc();

    Optional<Category> findBySlug(String slug);

    List<Category> findByParentIdOrderBySortOrderAscNameAscIdAsc(Long parentId);
}
