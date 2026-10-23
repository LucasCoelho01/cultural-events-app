package com.culturalevents.shared.pagination;

import org.springframework.data.domain.Page;

import com.culturalevents.shared.api.model.PageMetadata;

/**
 * Converte um {@link Page} do Spring Data no {@link PageMetadata} do contrato HTTP,
 * para que toda listagem paginada exponha os mesmos campos (page, size, totalElements, totalPages).
 */
public final class PageMetadataMapper {

    private PageMetadataMapper() {
    }

    public static PageMetadata from(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
