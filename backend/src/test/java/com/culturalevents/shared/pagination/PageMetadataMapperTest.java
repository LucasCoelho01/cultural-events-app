package com.culturalevents.shared.pagination;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.culturalevents.shared.api.model.PageMetadata;

import static org.assertj.core.api.Assertions.assertThat;

class PageMetadataMapperTest {

    @Test
    void mapeiaPaginaIntermediaria() {
        PageImpl<String> page = new PageImpl<>(
                List.of("a", "b", "c"), PageRequest.of(1, 3), 10);

        PageMetadata metadata = PageMetadataMapper.from(page);

        assertThat(metadata.getPage()).isEqualTo(1);
        assertThat(metadata.getSize()).isEqualTo(3);
        assertThat(metadata.getTotalElements()).isEqualTo(10L);
        assertThat(metadata.getTotalPages()).isEqualTo(4);
    }

    @Test
    void mapeiaUltimaPagina() {
        PageImpl<String> page = new PageImpl<>(
                List.of("j"), PageRequest.of(3, 3), 10);

        PageMetadata metadata = PageMetadataMapper.from(page);

        assertThat(metadata.getPage()).isEqualTo(3);
        assertThat(metadata.getSize()).isEqualTo(3);
        assertThat(metadata.getTotalElements()).isEqualTo(10L);
        assertThat(metadata.getTotalPages()).isEqualTo(4);
    }
}
