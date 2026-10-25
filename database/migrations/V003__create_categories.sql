-- V003: taxonomia de categorias (categoria > subcategoria, no máximo 2 níveis).
-- A tabela pertence ao back-end e só muda por scripts SQL numerados; a API é somente leitura.
-- O agente de ingestão apenas lê (lookup por slug, fallback em 'outros').

CREATE TABLE category (
    id         BIGSERIAL   PRIMARY KEY,
    parent_id  BIGINT      REFERENCES category (id),
    name       VARCHAR(80) NOT NULL,
    slug       VARCHAR(80) NOT NULL UNIQUE,
    sort_order INT         NOT NULL DEFAULT 0,
    CHECK (parent_id <> id)
);

CREATE INDEX category_parent_id_idx ON category (parent_id);

-- Garante o limite de 2 níveis contra qualquer escritor, inclusive scripts SQL manuais.
CREATE FUNCTION category_enforce_max_depth() RETURNS trigger AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL THEN
        -- FOR SHARE impede que o pai seja reparentado por outra transação até o commit.
        PERFORM 1 FROM category WHERE id = NEW.parent_id AND parent_id IS NOT NULL FOR SHARE;
        IF FOUND THEN
            RAISE EXCEPTION 'category % não pode ter pai %: máximo de 2 níveis', NEW.slug, NEW.parent_id
                USING ERRCODE = 'check_violation';
        END IF;

        IF TG_OP = 'UPDATE' AND EXISTS (SELECT 1 FROM category WHERE parent_id = NEW.id) THEN
            RAISE EXCEPTION 'category % tem subcategorias e não pode virar subcategoria', NEW.slug
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER category_max_depth
    BEFORE INSERT OR UPDATE OF parent_id ON category
    FOR EACH ROW EXECUTE FUNCTION category_enforce_max_depth();

INSERT INTO category (name, slug, sort_order) VALUES
    ('Shows',      'shows',      10),
    ('Teatro',     'teatro',     20),
    ('Exposições', 'exposicoes', 30),
    ('Dança',      'danca',      40),
    ('Cinema',     'cinema',     50),
    ('Outros',     'outros',     60);
