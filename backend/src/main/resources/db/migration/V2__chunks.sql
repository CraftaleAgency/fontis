-- Retrieval chunks: long units are split into paragraph groups; each chunk keeps its unit
-- so answers can cite the exact article. Embeddings come from bge-m3 (1024 dimensions).
CREATE TABLE chunk (
    celex        TEXT    NOT NULL,
    lang         TEXT    NOT NULL,
    unit_id      TEXT    NOT NULL,
    chunk_index  INT     NOT NULL,
    content      TEXT    NOT NULL,
    embedding    vector(1024) NOT NULL,
    -- Full-text search with the stemmer of the chunk's language.
    tsv          tsvector GENERATED ALWAYS AS (
                     to_tsvector(
                         CASE lang
                             WHEN 'eng' THEN 'english'::regconfig
                             WHEN 'ita' THEN 'italian'::regconfig
                             WHEN 'deu' THEN 'german'::regconfig
                             WHEN 'fra' THEN 'french'::regconfig
                             WHEN 'spa' THEN 'spanish'::regconfig
                             ELSE 'simple'::regconfig
                         END,
                         content)
                 ) STORED,
    PRIMARY KEY (celex, lang, unit_id, chunk_index),
    FOREIGN KEY (celex, lang, unit_id) REFERENCES source_unit (celex, lang, unit_id) ON DELETE CASCADE
);

CREATE INDEX chunk_embedding_idx ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX chunk_tsv_idx ON chunk USING gin (tsv);
CREATE INDEX chunk_lang_idx ON chunk (lang);
