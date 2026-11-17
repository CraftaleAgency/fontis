-- When each act and unit applies, and which units of the same act a unit refers to.
-- Filled by the corpus ingestion from documents.json and the unit JSONL (fontis-bench corpus build).

ALTER TABLE source_document
    ADD COLUMN entry_into_force  DATE,
    -- Date from which the act applies; a unit may apply from another date (source_unit.applies_from).
    ADD COLUMN applies_from      DATE,
    -- Date of the text in the corpus (publication in the Official Journal): later amendments are not in it.
    ADD COLUMN text_version      DATE,
    -- The unit holding the act's definitions, e.g. art_3 in the AI Act.
    ADD COLUMN definitions_unit  TEXT,
    ADD COLUMN note              TEXT;

ALTER TABLE source_unit
    -- Unit ids of the same act that this unit refers to explicitly (e.g. {art_3, anx_III}).
    ADD COLUMN refs                TEXT[] NOT NULL DEFAULT '{}',
    -- NULL: the unit applies from the act's date.
    ADD COLUMN applies_from        DATE,
    ADD COLUMN applicability_note  TEXT;
