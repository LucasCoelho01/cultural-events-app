-- V005: eventos e sessões (occurrence), com as invariantes no banco para valer também para o agente de ingestão.
-- Constraints e RAISE usam nomes explícitos: a aplicação identifica a regra violada pelo nome da constraint.
-- As mensagens dos RAISE trazem 'constraint "nome"' no texto padrão do Postgres, de onde o nome também é extraído.
-- search_vector e locked_fields ficam para as fases 2 e 3.

CREATE TABLE event (
    id              BIGSERIAL     PRIMARY KEY,
    public_id       UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    city_id         BIGINT        NOT NULL REFERENCES city (id),
    title           VARCHAR(255)  NOT NULL,
    slug            VARCHAR(255)  NOT NULL,
    description     TEXT,
    category_id     BIGINT        NOT NULL REFERENCES category (id),
    venue_id        BIGINT        REFERENCES venue (id),
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    age_rating      VARCHAR(10),
    price_min_cents INT,
    price_max_cents INT,
    is_free         BOOLEAN       NOT NULL DEFAULT false,
    ticket_url      TEXT,
    image_url       TEXT,
    origin          VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version         INT           NOT NULL DEFAULT 0,
    CONSTRAINT event_city_slug_key UNIQUE (city_id, slug),
    CONSTRAINT event_status_valid CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT event_origin_valid CHECK (origin IN ('MANUAL', 'AGENT')),
    CONSTRAINT event_price_min_nonneg CHECK (price_min_cents >= 0),
    CONSTRAINT event_price_range CHECK (price_max_cents >= price_min_cents),
    CONSTRAINT event_free_no_price CHECK (
        NOT is_free OR (COALESCE(price_min_cents, 0) = 0 AND COALESCE(price_max_cents, 0) = 0))
);

CREATE INDEX event_city_status_idx ON event (city_id, status);
-- O Postgres não indexa FKs sozinho: filtro por categoria e a checagem de eventos ao remover um local.
CREATE INDEX event_category_idx ON event (category_id);
CREATE INDEX event_venue_idx ON event (venue_id);

CREATE TABLE occurrence (
    id        BIGSERIAL    PRIMARY KEY,
    event_id  BIGINT       NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ  NOT NULL,
    ends_at   TIMESTAMPTZ,
    status    VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT occurrence_event_starts_at_key UNIQUE (event_id, starts_at),
    CONSTRAINT occurrence_time_order CHECK (ends_at >= starts_at),
    CONSTRAINT occurrence_status_valid CHECK (status IN ('SCHEDULED', 'CANCELLED', 'SOLD_OUT'))
);

CREATE INDEX occurrence_starts_at_idx ON occurrence (starts_at);

-- Slug a partir do título quando vier nulo ou vazio; roda antes das checagens de NOT NULL e unicidade.
CREATE FUNCTION event_fill_slug() RETURNS trigger AS $$
BEGIN
    IF NEW.slug IS NULL OR btrim(NEW.slug) = '' THEN
        NEW.slug := left(slugify(NEW.title), 255);
        IF NEW.slug = '' THEN
            RAISE EXCEPTION 'violates check constraint "event_slug_not_empty": title % não gera um slug válido', NEW.title
                USING ERRCODE = 'check_violation', CONSTRAINT = 'event_slug_not_empty';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER event_fill_slug
    BEFORE INSERT OR UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION event_fill_slug();

CREATE TRIGGER event_set_updated_at
    BEFORE UPDATE ON event
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- O local do evento precisa ser da mesma cidade do evento.
CREATE FUNCTION event_check_venue_city() RETURNS trigger AS $$
BEGIN
    -- Local inexistente fica por conta da FK (23503); aqui só a divergência de cidade.
    IF NEW.venue_id IS NOT NULL
            AND EXISTS (SELECT 1 FROM venue WHERE id = NEW.venue_id AND city_id <> NEW.city_id) THEN
        RAISE EXCEPTION 'violates check constraint "event_venue_city_match": event % tem venue % de outra cidade', NEW.slug, NEW.venue_id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'event_venue_city_match';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER event_check_venue_city
    BEFORE INSERT OR UPDATE OF venue_id, city_id ON event
    FOR EACH ROW EXECUTE FUNCTION event_check_venue_city();

-- Lado do local: sem isto, mudar a cidade de um local com eventos quebraria a invariante acima.
CREATE FUNCTION venue_guard_city_change() RETURNS trigger AS $$
BEGIN
    IF NEW.city_id <> OLD.city_id AND EXISTS (SELECT 1 FROM event WHERE venue_id = OLD.id) THEN
        RAISE EXCEPTION 'violates restrict constraint "venue_city_in_use": venue % tem eventos e não pode mudar de cidade', OLD.slug
            USING ERRCODE = 'restrict_violation', CONSTRAINT = 'venue_city_in_use';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER venue_guard_city_change
    BEFORE UPDATE OF city_id ON venue
    FOR EACH ROW EXECUTE FUNCTION venue_guard_city_change();

-- Igual à da V004 (que não é editada), mas agora com o nome da constraint no erro.
CREATE OR REPLACE FUNCTION venue_fill_slug() RETURNS trigger AS $$
BEGIN
    IF NEW.slug IS NULL OR btrim(NEW.slug) = '' THEN
        NEW.slug := left(slugify(NEW.name), 200);
        IF NEW.slug = '' THEN
            RAISE EXCEPTION 'violates check constraint "venue_slug_not_empty": name % não gera um slug válido', NEW.name
                USING ERRCODE = 'check_violation', CONSTRAINT = 'venue_slug_not_empty';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
