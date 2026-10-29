-- V006: busca textual de eventos.
-- event.search_vector é calculado por trigger (e não por coluna gerada, que não aceita subconsulta nem unaccent)
-- para valer também para o agente de ingestão, que grava direto no banco. Nenhum escritor define o vetor.
-- Pesos: título A; nome da categoria e do local B; descrição C. Dicionário 'portuguese' sobre texto sem acentos
-- e sem o "s" final do plural (o stemmer portuguese não junta "show" e "shows"; palavras estrangeiras são comuns).
-- A consulta aplica a mesma event_search_text(), então vetor e termo buscado são normalizados do mesmo jeito.
--
-- Limitação aceita: renomear uma categoria NÃO atualiza o vetor dos eventos (categorias só mudam por script
-- numerado e a operação é rara). O script que renomear uma categoria deve incluir, depois do UPDATE:
--     UPDATE event SET title = title WHERE category_id = <id>;
-- Renomear um local atualiza os eventos dele (trigger venue_refresh_event_search), pois a API edita locais.

ALTER TABLE event ADD COLUMN search_vector tsvector NOT NULL DEFAULT ''::tsvector;

-- Texto sem acentos, em minúsculas e sem o "s" final de palavras com 4 ou mais letras ("shows" -> "show").
CREATE FUNCTION event_search_text(value text) RETURNS text AS $$
    SELECT regexp_replace(lower(unaccent(coalesce(value, ''))), '([a-z]{3,})s\M', '\1', 'g');
$$ LANGUAGE sql STABLE;

CREATE FUNCTION event_build_search_vector(
    p_title       text,
    p_description text,
    p_category_id bigint,
    p_venue_id    bigint
) RETURNS tsvector AS $$
    SELECT setweight(to_tsvector('portuguese', event_search_text(p_title)), 'A')
        || setweight(to_tsvector('portuguese', event_search_text(
               coalesce((SELECT name FROM category WHERE id = p_category_id), '') || ' '
            || coalesce((SELECT name FROM venue WHERE id = p_venue_id), ''))), 'B')
        || setweight(to_tsvector('portuguese', event_search_text(p_description)), 'C');
$$ LANGUAGE sql STABLE;

CREATE FUNCTION event_set_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := event_build_search_vector(NEW.title, NEW.description, NEW.category_id, NEW.venue_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Roda depois de event_fill_slug (ordem alfabética) e sobrescreve qualquer search_vector enviado pelo escritor.
CREATE TRIGGER event_set_search_vector
    BEFORE INSERT OR UPDATE OF title, description, category_id, venue_id ON event
    FOR EACH ROW EXECUTE FUNCTION event_set_search_vector();

-- Local renomeado: regrava os eventos dele para o trigger acima recalcular o vetor (updated_at avança, version não).
CREATE FUNCTION venue_refresh_event_search() RETURNS trigger AS $$
BEGIN
    IF NEW.name IS DISTINCT FROM OLD.name THEN
        UPDATE event SET title = title WHERE venue_id = NEW.id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER venue_refresh_event_search
    AFTER UPDATE OF name ON venue
    FOR EACH ROW EXECUTE FUNCTION venue_refresh_event_search();

-- Eventos existentes: o UPDATE aciona o trigger. O índice vem depois, para ser construído de uma vez só.
UPDATE event SET title = title;

CREATE INDEX event_search_vector_idx ON event USING GIN (search_vector);
