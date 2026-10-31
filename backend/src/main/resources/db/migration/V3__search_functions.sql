-- Postgres text-search configuration for a corpus language code.
CREATE FUNCTION fontis_regconfig(lang TEXT) RETURNS regconfig
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE lang
        WHEN 'eng' THEN 'english'::regconfig
        WHEN 'ita' THEN 'italian'::regconfig
        WHEN 'deu' THEN 'german'::regconfig
        WHEN 'fra' THEN 'french'::regconfig
        WHEN 'spa' THEN 'spanish'::regconfig
        ELSE 'simple'::regconfig
    END
$$;

-- A natural-language question as an OR query: any stemmed term may match, ranking does the rest.
-- plainto_tsquery would AND every term, which is too strict for full questions.
-- Returns NULL when the question has no lexemes left (only stop words).
CREATE FUNCTION fontis_or_query(lang TEXT, question TEXT) RETURNS tsquery
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT nullif(replace(plainto_tsquery(fontis_regconfig(lang), question)::text, ' & ', ' | '), '')::tsquery
$$;

-- The distinct stemmed terms of a question, to count how many of them a chunk contains.
-- Ranking by that count first keeps a frequent generic term (e.g. "AI" in the AI Act) from
-- outranking a chunk that matches several distinct terms of the question.
CREATE FUNCTION fontis_query_lexemes(lang TEXT, question TEXT) RETURNS TEXT[]
    LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT tsvector_to_array(to_tsvector(fontis_regconfig(lang), question))
$$;
