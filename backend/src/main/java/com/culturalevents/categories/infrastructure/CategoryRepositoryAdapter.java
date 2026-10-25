package com.culturalevents.categories.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.culturalevents.categories.domain.Category;
import com.culturalevents.categories.domain.CategoryRepository;

@Repository
class CategoryRepositoryAdapter implements CategoryRepository {

    private final SpringDataCategoryRepository delegate;

    CategoryRepositoryAdapter(SpringDataCategoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Category> findAllOrdered() {
        return delegate.findAllByOrderBySortOrderAscNameAscIdAsc();
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return delegate.findBySlug(slug);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Category> findChildrenOrdered(Long parentId) {
        return delegate.findByParentIdOrderBySortOrderAscNameAscIdAsc(parentId);
    }
}
