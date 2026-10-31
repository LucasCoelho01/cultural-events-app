package com.culturalevents.ingestion.domain;

import java.util.List;

public interface SourceRepository {

    /** Todas as fontes, ordenadas por nome. */
    List<Source> findAll();
}
