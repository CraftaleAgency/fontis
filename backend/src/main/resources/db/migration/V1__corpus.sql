-- Legislation corpus: one row per citable unit (article, recital, annex) per language.
-- The unit id (e.g. art_5) is the same in every language, which enables cross-lingual alignment.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE source_document (
    celex       TEXT PRIMARY KEY,
    short_name  TEXT NOT NULL,
    title       TEXT NOT NULL
);

CREATE TABLE source_unit (
    celex       TEXT NOT NULL REFERENCES source_document (celex),
    lang        TEXT NOT NULL,
    unit_id     TEXT NOT NULL,
    unit_type   TEXT NOT NULL CHECK (unit_type IN ('article', 'recital', 'annex')),
    label       TEXT NOT NULL,
    heading     TEXT,
    body        TEXT NOT NULL,
    url         TEXT NOT NULL,
    PRIMARY KEY (celex, lang, unit_id)
);

CREATE INDEX source_unit_fts_idx ON source_unit USING GIN (to_tsvector('simple', body));
