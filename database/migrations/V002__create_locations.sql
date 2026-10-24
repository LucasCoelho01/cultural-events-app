-- V002: estados e cidades, base do comportamento multicidade.
-- Novas cidades são cadastradas por script SQL numerado; a API é somente leitura.

CREATE TABLE state (
    uf   CHAR(2)     PRIMARY KEY,
    name VARCHAR(60) NOT NULL
);

CREATE TABLE city (
    id        BIGSERIAL     PRIMARY KEY,
    ibge_code INT           NOT NULL UNIQUE,
    state_uf  CHAR(2)       NOT NULL REFERENCES state (uf),
    name      VARCHAR(120)  NOT NULL,
    slug      VARCHAR(120)  NOT NULL,
    timezone  VARCHAR(40)   NOT NULL,
    latitude  NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    active    BOOLEAN       NOT NULL DEFAULT false,
    UNIQUE (state_uf, slug)
);

INSERT INTO state (uf, name) VALUES
    ('AC', 'Acre'),
    ('AL', 'Alagoas'),
    ('AP', 'Amapá'),
    ('AM', 'Amazonas'),
    ('BA', 'Bahia'),
    ('CE', 'Ceará'),
    ('DF', 'Distrito Federal'),
    ('ES', 'Espírito Santo'),
    ('GO', 'Goiás'),
    ('MA', 'Maranhão'),
    ('MT', 'Mato Grosso'),
    ('MS', 'Mato Grosso do Sul'),
    ('MG', 'Minas Gerais'),
    ('PA', 'Pará'),
    ('PB', 'Paraíba'),
    ('PR', 'Paraná'),
    ('PE', 'Pernambuco'),
    ('PI', 'Piauí'),
    ('RJ', 'Rio de Janeiro'),
    ('RN', 'Rio Grande do Norte'),
    ('RS', 'Rio Grande do Sul'),
    ('RO', 'Rondônia'),
    ('RR', 'Roraima'),
    ('SC', 'Santa Catarina'),
    ('SP', 'São Paulo'),
    ('SE', 'Sergipe'),
    ('TO', 'Tocantins');

INSERT INTO city (ibge_code, state_uf, name, slug, timezone, latitude, longitude, active) VALUES
    (3550308, 'SP', 'São Paulo', 'sao-paulo', 'America/Sao_Paulo', -23.550520, -46.633308, true);
