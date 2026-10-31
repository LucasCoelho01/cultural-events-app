package com.culturalevents.ingestion.infrastructure;

import java.util.List;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Repository;

import com.culturalevents.ingestion.domain.Source;
import com.culturalevents.ingestion.domain.SourceRepository;

@Repository
class SourceRepositoryAdapter implements SourceRepository {

    private final EntityManager entityManager;

    SourceRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Source> findAll() {
        return entityManager.createQuery("select s from Source s order by s.name, s.id", Source.class)
                .getResultList();
    }
}
