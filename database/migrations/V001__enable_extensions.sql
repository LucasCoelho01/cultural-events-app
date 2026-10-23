-- V001: habilita extensions do Postgres usadas por busca textual em capabilities futuras.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
