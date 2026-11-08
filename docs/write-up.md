# Cited or nothing: building and measuring a sovereign RAG assistant for EU law

*Fontis, eight weeks in: what worked, what the benchmark caught, and what a 14-billion-parameter
model on one consumer GPU can and cannot do.*

## The problem

European organisations want to ask questions about regulations and their own documents in their
own language, and they want to know where an answer comes from. Most assistants either send the
documents to a provider outside the EU or produce fluent answers with no way to check them. The
AI Act adds transparency duties on top.

Fontis is an attempt at the opposite: an open-source assistant that **answers only from sources it
can cite**, **refuses when it has none**, works across **five EU languages**, and runs entirely on
infrastructure the organisation controls — here, `qwen3:14b` and `bge-m3` on a single 12 GB RTX
3060, with Postgres and pgvector.

The demo corpus is the AI Act, the GDPR, NIS2 and the Data Act in English, Italian, German, French
and Spanish: 4,700 citable units (articles, recitals, annexes) whose ids are aligned across
languages, so `art_5` of the AI Act is the same article in every version.

## Architecture in one paragraph

A Spring Boot 4 service with Spring AI ingests the corpus with Spring Batch, splits each unit into
paragraph-level chunks, embeds them with `bge-m3` and stores them in pgvector next to a per-language
full-text index. A question is embedded once and matched against chunks in **every** language; the
results are fused, collapsed to one result per unit, and the best units are given to the model with
numbers. The model must end each factual sentence with a source number, or answer `NO_ANSWER`.
Uploaded documents follow the same path, and permissions are enforced **inside the retrieval
query**: a document a user may not read never reaches the model. A Next.js front end shows every
source with a link to EUR-Lex in the reader's language, labels answers as AI-generated and keeps an
answer log without IP addresses.

## Measuring it

Claims about RAG quality are cheap, so the project has a benchmark from week 4, now published as
[Fontis EU Law QA](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa): 150 questions, 30 base
questions asked in five languages, each answerable one tied to its gold article(s) and an English
reference answer, plus 30 questions that should be refused. Questions are phrased the way people
ask ("Can I ask a company to delete my personal data?"), not copied from article titles. Answers are
graded by a different model (`gemma4:12b`) than the one that writes them, and the raw verdicts are
kept for manual review.

## What the benchmark caught

### 1. The obvious hybrid search was the worst option

The first version fused vector and full-text search with equal weights, the textbook default. The
gold article came first for **40%** of questions; vector search alone managed 68%. Two reasons
showed up in the data:

- **Recitals were counted twice.** Recitals paraphrase the articles they explain, so both searches
  find them and fusion promoted them: a recital ranked first for 33 of 75 questions. Users need, and
  should cite, the binding article. Weighting recitals at 0.6 was the largest single gain.
- **Word matching does not survive paraphrase.** Users say "banned", the law says "prohibited";
  "delete my data", the law says "erasure". Multilingual embeddings bridge that, full-text search
  does not, so its weight went to zero.

Result: **79%** first-rank accuracy, up from 40%. Full-text search remains available for exact
references such as article numbers.

### 2. Cross-lingual retrieval is not a nice-to-have

Around a third of the gold articles found in the top five matched a text in **another language**
than the question — an Italian question finding the French text of AI Act Article 5. Restricting
search to the question's language would have thrown those away. Aligned unit ids across languages
make this safe: whichever language matched, the citation points to the same article, shown in the
reader's language.

### 3. One chunk per source is not enough

The NIS2 maximum fine was answered wrongly in three of five languages. Retrieval was right; the
model received only the best-matching chunk of Article 34, which states the fine for *important*
entities, not *essential* ones. Giving the model up to 2,400 characters of each source — the
retrieved chunks plus their neighbours, in reading order — fixed it in all five languages and
raised correct answers from 59% to **67%**, for about 1.3 seconds more per answer.

### 4. It refuses, including near misses

All unanswerable questions were refused in every language — 30 of 30 in v2 — including three
designed to tempt the model: the Digital Services Act and the Cyber Resilience Act, which resemble
NIS2 and the GDPR but are not in the corpus, and the amount of a specific GDPR fine, which is an
enforcement decision rather than law. A third of refusals come from a similarity threshold before
the model is called, the rest from the model.

### 5. Held-out questions: retrieval generalises, reasoning is the limit

The ranking was tuned on the v1 questions, so v2 adds 9 answerable questions never used for
tuning. On those, the gold article is first **76%** of the time and in the top five **98%** of the
time, against 79% and 84% on the questions it was tuned on: the tuning did not overfit.

Answer quality is lower on the new questions (53% correct, 74% score), and the reasons are
instructive:

- **Lists get cut short.** Questions such as "which cybersecurity measures does NIS2 require?" have
  ten-item answers; the prompt asks for at most six sentences, and the judge marks incomplete lists
  as partial. That is a product choice worth revisiting, not a retrieval failure.
- **Comparisons go wrong with the right sources.** Asked whether the GDPR or the AI Act has higher
  maximum fines, Fontis retrieved and cited both articles in all five languages. In French and
  Spanish the model quoted EUR 35 million or 7 % for the AI Act and EUR 20 million or 4 % for the
  GDPR — and concluded that the GDPR's fines are higher.

That second case is the honest limit of a 14B model on one consumer GPU: citations make the error
visible and checkable, but they do not prevent it. A larger answer model, or a step that checks
numeric claims against the cited text, is the next lever.

## Headline numbers (v2)

| | |
|---|---:|
| Gold article ranked first / in the top 5 | 78% / 89% |
| Answers citing a gold article | 88% |
| Answers judged correct / correct or partial | 64% / 96% |
| Unanswerable questions refused | 30 / 30 |
| Median generation time | 9.0 s |

## Running it in production

The deployment is one Docker Compose stack for any EU server: Caddy with automatic HTTPS, the web
app, the backend, Postgres with pgvector and, optionally, Ollama on a GPU. Only Caddy is exposed.
Prometheus and a provisioned Grafana dashboard track outcomes by language, retrieval and generation
time and token use; a per-client rate limit protects the GPU.

Each question is also one **trace in Langfuse**, sent over OpenTelemetry: `answer-question` with
`retrieve-context`, the embedding and the generation nested under it, stable names that survive a
model change, and refusals flagged as warnings. Framework noise (HTTP, security, Spring AI's
internal wrappers) is filtered before export. By default traces carry structure, timings, models
and token counts but **not the questions or answers**: sending content is an explicit setting,
because questions can contain personal data.

## Lessons

1. **Measure before tuning.** The equal-weight hybrid search looked reasonable and was the worst
   configuration tested. Without a benchmark it would have shipped.
2. **Keep the judge's reasons.** Manual review found a judge error among four "incorrect" verdicts in
   v1; numbers without reasons would have hidden it.
3. **Hold questions out.** The 9 held-out questions are what make the retrieval numbers credible.
4. **Refusal is a feature to test**, with questions designed to tempt the model, not only obviously
   off-topic ones.
5. **Citations are necessary, not sufficient.** They make every claim checkable; they do not make the
   model reason correctly over what it cites.

## Try it

- Code (Apache-2.0): [github.com/CraftaleAgency/fontis](https://github.com/CraftaleAgency/fontis)
- Benchmark dataset (CC BY 4.0): [huggingface.co/datasets/Fla00/fontis-eu-law-qa](https://huggingface.co/datasets/Fla00/fontis-eu-law-qa)
- Benchmark reports: [docs/benchmark](benchmark/README.md)
- Deployment and observability: [deployment](deployment.md), [observability](observability.md)
