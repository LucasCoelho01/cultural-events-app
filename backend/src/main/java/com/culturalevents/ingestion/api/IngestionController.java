package com.culturalevents.ingestion.api;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.culturalevents.ingestion.application.IngestionService;
import com.culturalevents.ingestion.domain.IngestionRun;
import com.culturalevents.shared.api.IngestionApi;
import com.culturalevents.shared.api.model.IngestionRunDetail;
import com.culturalevents.shared.api.model.IngestionRunPage;
import com.culturalevents.shared.api.model.IngestionRunStatus;
import com.culturalevents.shared.api.model.SourceRef;
import com.culturalevents.shared.api.model.UnpublishResult;
import com.culturalevents.shared.pagination.PageMetadataMapper;

@RestController
class IngestionController implements IngestionApi {

    private final IngestionService service;

    IngestionController(IngestionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<IngestionRunPage> listIngestionRuns(String source, IngestionRunStatus status, Integer page,
            Integer size) {
        com.culturalevents.ingestion.domain.IngestionRunStatus domainStatus = status == null ? null
                : com.culturalevents.ingestion.domain.IngestionRunStatus.valueOf(status.name());
        Page<IngestionRun> result = service.list(source, domainStatus, page, size);
        return ResponseEntity.ok(new IngestionRunPage(
                result.getContent().stream().map(IngestionMapper::toSummary).toList(),
                PageMetadataMapper.from(result)));
    }

    @Override
    public ResponseEntity<List<SourceRef>> listIngestionSources() {
        return ResponseEntity.ok(service.listSources().stream().map(IngestionMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<IngestionRunDetail> getIngestionRun(Long id) {
        return ResponseEntity.ok(IngestionMapper.toDetail(service.get(id)));
    }

    @Override
    public ResponseEntity<UnpublishResult> unpublishIngestionRun(Long id) {
        return ResponseEntity.ok(new UnpublishResult(service.unpublish(id)));
    }
}
