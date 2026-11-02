-- Users, groups and documents uploaded next to the EU legislation corpus.
-- source_document.celex is the document id: a CELEX number for EU legislation, "doc_..." for uploads.

-- ---------- Users and groups ----------

CREATE TABLE app_user (
    id             BIGSERIAL   PRIMARY KEY,
    email          TEXT        NOT NULL UNIQUE,
    display_name   TEXT        NOT NULL,
    password_hash  TEXT        NOT NULL,
    role           TEXT        NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_group (
    id    BIGSERIAL PRIMARY KEY,
    name  TEXT      NOT NULL UNIQUE
);

CREATE TABLE user_group (
    user_id   BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    group_id  BIGINT NOT NULL REFERENCES app_group (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, group_id)
);

-- ---------- Documents ----------

ALTER TABLE source_document
    ADD COLUMN kind        TEXT NOT NULL DEFAULT 'eu-law' CHECK (kind IN ('eu-law', 'upload')),
    -- public: everyone, signed in or not. restricted: administrators and the groups in document_group.
    ADD COLUMN visibility  TEXT NOT NULL DEFAULT 'public' CHECK (visibility IN ('public', 'restricted')),
    ADD COLUMN lang        TEXT,
    ADD COLUMN filename    TEXT,
    ADD COLUMN uploaded_by BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN created_at  TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE document_group (
    celex     TEXT   NOT NULL REFERENCES source_document (celex) ON DELETE CASCADE,
    group_id  BIGINT NOT NULL REFERENCES app_group (id) ON DELETE CASCADE,
    PRIMARY KEY (celex, group_id)
);

-- Uploaded documents are split into pages (PDF) or sections (other formats).
ALTER TABLE source_unit DROP CONSTRAINT source_unit_unit_type_check;
ALTER TABLE source_unit ADD CONSTRAINT source_unit_unit_type_check
    CHECK (unit_type IN ('article', 'recital', 'annex', 'page', 'section'));

-- Deleting a document deletes its units, and through them its chunks.
ALTER TABLE source_unit DROP CONSTRAINT source_unit_celex_fkey;
ALTER TABLE source_unit ADD CONSTRAINT source_unit_celex_fkey
    FOREIGN KEY (celex) REFERENCES source_document (celex) ON DELETE CASCADE;
