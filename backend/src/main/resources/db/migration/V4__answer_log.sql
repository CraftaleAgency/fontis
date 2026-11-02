-- Answer log (AI Act record-keeping): every question with its outcome, cited sources, model and timings.
-- No IP address or user identifier is stored; entries are deleted after fontis.log.retention-days.
CREATE TABLE answer_log (
    id             BIGSERIAL   PRIMARY KEY,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    question       TEXT        NOT NULL,
    lang           TEXT        NOT NULL,
    status         TEXT        NOT NULL,
    answer         TEXT,
    citations      JSONB       NOT NULL,
    model          TEXT,
    retrieval_ms   INT         NOT NULL,
    generation_ms  INT         NOT NULL
);

CREATE INDEX answer_log_created_at_idx ON answer_log (created_at DESC);
