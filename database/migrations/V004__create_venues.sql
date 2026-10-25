-- V004: locais (venues) por cidade e regras reutilizáveis de slug e updated_at.
-- slugify() e set_updated_at() são genéricas e podem ser usadas por outras tabelas (ex.: event).
-- As regras de slug e updated_at vivem no banco para valer também para escritores diretos (agente de ingestão).

-- unaccent não é IMMUTABLE, então slugify também é só STABLE.
CREATE FUNCTION slugify(value text) RETURNS text AS $$
    SELECT trim(both '-' from regexp_replace(lower(unaccent(value)), '[^a-z0-9]+', '-', 'g'));
$$ LANGUAGE sql STABLE;

-- clock_timestamp() (e não now()) para mudar mesmo quando INSERT e UPDATE ocorrem na mesma transação.
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE venue (
    id           BIGSERIAL     PRIMARY KEY,
    public_id    UUID          NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    city_id      BIGINT        NOT NULL REFERENCES city (id),
    name         VARCHAR(200)  NOT NULL,
    slug         VARCHAR(200)  NOT NULL,
    address_line VARCHAR(255),
    neighborhood VARCHAR(120),
    postal_code  VARCHAR(9),
    latitude     NUMERIC(9, 6),
    longitude    NUMERIC(9, 6),
    website_url  TEXT,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (city_id, slug),
    CONSTRAINT venue_latitude_range CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT venue_longitude_range CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT venue_postal_code_format CHECK (postal_code ~ '^\d{5}-\d{3}$')
);

-- Preenche o slug a partir do nome quando vier nulo ou vazio; roda antes das checagens de NOT NULL e unicidade.
CREATE FUNCTION venue_fill_slug() RETURNS trigger AS $$
BEGIN
    IF NEW.slug IS NULL OR btrim(NEW.slug) = '' THEN
        NEW.slug := left(slugify(NEW.name), 200);
        IF NEW.slug = '' THEN
            RAISE EXCEPTION 'venue name % não gera um slug válido', NEW.name
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER venue_fill_slug
    BEFORE INSERT OR UPDATE ON venue
    FOR EACH ROW EXECUTE FUNCTION venue_fill_slug();

CREATE TRIGGER venue_set_updated_at
    BEFORE UPDATE ON venue
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
