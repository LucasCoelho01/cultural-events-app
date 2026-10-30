-- V008: role do agente de ingestão. Só DML nas tabelas que o agente escreve; nenhum DDL.
-- A senha NÃO fica no script versionado: defina à mão (ver README):
--     ALTER ROLE agent_writer PASSWORD '...';
-- Roles pertencem ao cluster, então a criação é condicional (o script pode rodar de novo com o banco recriado).
-- Sem ALTER DEFAULT PRIVILEGES: tabela nova só é escrita pelo agente se a migration que a cria conceder o acesso.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'agent_writer') THEN
        CREATE ROLE agent_writer LOGIN;
    END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO agent_writer;
REVOKE CREATE ON SCHEMA public FROM agent_writer;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO agent_writer;

GRANT INSERT, UPDATE, DELETE ON
    event, occurrence, venue, source, event_source_link, ingestion_run
    TO agent_writer;

GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO agent_writer;
