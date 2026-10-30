-- V007: schema de ingestão, o contrato do banco com o agente externo (Python) que grava direto no Postgres.
-- Fontes, vínculo evento-fonte (idempotência e lápides), registro de execuções, versão do contrato e event.locked_fields.
-- Constraints com nomes explícitos, como nas demais tabelas.

-- Fontes de eventos. city_id nulo = fonte nacional.
CREATE TABLE source (
    id         BIGSERIAL     PRIMARY KEY,
    code       VARCHAR(60)   NOT NULL,
    name       VARCHAR(120)  NOT NULL,
    base_url   TEXT          NOT NULL,
    city_id    BIGINT        REFERENCES city (id),
    active     BOOLEAN       NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT source_code_key UNIQUE (code)
);

CREATE INDEX source_city_idx ON source (city_id);

CREATE TRIGGER source_set_updated_at
    BEFORE UPDATE ON source
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO source (code, name, base_url, city_id, active)
VALUES ('sympla', 'Sympla', 'https://www.sympla.com.br', NULL, true);

-- Execuções do agente. errors é uma lista JSON de erros por item.
CREATE TABLE ingestion_run (
    id          BIGSERIAL    PRIMARY KEY,
    source_id   BIGINT       NOT NULL REFERENCES source (id),
    started_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    status      VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    created     INT          NOT NULL DEFAULT 0,
    updated     INT          NOT NULL DEFAULT 0,
    unchanged   INT          NOT NULL DEFAULT 0,
    failed      INT          NOT NULL DEFAULT 0,
    errors      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT ingestion_run_status_valid CHECK (status IN ('RUNNING', 'SUCCESS', 'PARTIAL', 'FAILED')),
    CONSTRAINT ingestion_run_counters_nonneg CHECK (created >= 0 AND updated >= 0 AND unchanged >= 0 AND failed >= 0),
    CONSTRAINT ingestion_run_time_order CHECK (finished_at >= started_at)
);

CREATE INDEX ingestion_run_source_started_idx ON ingestion_run (source_id, started_at DESC);

-- Vínculo entre um evento e o item da fonte que o originou.
-- event_id nulo = lápide: o evento foi excluído à mão e o agente não deve recriá-lo para este (source_id, external_id).
CREATE TABLE event_source_link (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     BIGINT       REFERENCES event (id) ON DELETE SET NULL,
    source_id    BIGINT       NOT NULL REFERENCES source (id),
    external_id  VARCHAR(255) NOT NULL,
    external_url TEXT,
    content_hash VARCHAR(64),
    last_run_id  BIGINT       REFERENCES ingestion_run (id) ON DELETE SET NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT event_source_link_source_external_key UNIQUE (source_id, external_id)
);

-- Também serve ao ON DELETE SET NULL ao excluir um evento.
CREATE INDEX event_source_link_event_idx ON event_source_link (event_id);
CREATE INDEX event_source_link_last_run_idx ON event_source_link (last_run_id);

CREATE TRIGGER event_source_link_set_updated_at
    BEFORE UPDATE ON event_source_link
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Versão do contrato MAJOR.MINOR; o agente lê a maior na partida e para se o MAJOR mudar.
CREATE TABLE schema_contract (
    version    VARCHAR(10)  NOT NULL,
    applied_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT schema_contract_version_key PRIMARY KEY (version),
    CONSTRAINT schema_contract_version_format CHECK (version ~ '^\d+\.\d+$')
);

INSERT INTO schema_contract (version) VALUES ('1.0');

-- Campos de event alterados à mão (nomes de coluna, mais o nome especial 'occurrences' para as sessões),
-- mantidos pelo back-end; o agente não os sobrescreve.
ALTER TABLE event ADD COLUMN locked_fields TEXT[] NOT NULL DEFAULT '{}';
