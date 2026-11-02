"use client";

import { useEffect, useRef, useState } from "react";
import { ask, sourceLabel, splitCitations, type AskResponse, type Citation } from "@/lib/api";
import { backendLang, corpusLanguageName, dictionaries, type Locale } from "@/lib/i18n";

type Turn = { id: number; question: string; response?: AskResponse; failed?: boolean };

export default function Chat({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const [turns, setTurns] = useState<Turn[]>([]);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);
  const nextId = useRef(0);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [turns]);

  async function submit(question: string) {
    const text = question.trim();
    if (!text || busy) {
      return;
    }
    const id = ++nextId.current;
    setTurns((current) => [...current, { id, question: text }]);
    setDraft("");
    setBusy(true);
    try {
      const response = await ask(text, backendLang[locale]);
      setTurns((current) => current.map((turn) => (turn.id === id ? { ...turn, response } : turn)));
    } catch {
      setTurns((current) => current.map((turn) => (turn.id === id ? { ...turn, failed: true } : turn)));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      {turns.length === 0 && (
        <section>
          <h1 className="text-2xl font-semibold tracking-tight">{t.tagline}</h1>
          <p className="mt-6 text-sm font-medium text-muted">{t.examplesTitle}</p>
          <ul className="mt-2 grid gap-2 sm:grid-cols-2">
            {t.examples.map((example) => (
              <li key={example}>
                <button
                  type="button"
                  onClick={() => submit(example)}
                  className="h-full w-full rounded-lg border border-line bg-surface px-4 py-3 text-left text-sm transition-colors hover:border-accent hover:text-accent"
                >
                  {example}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      <div aria-live="polite" className="flex flex-col gap-6">
        {turns.map((turn) => (
          <article key={turn.id} className="flex flex-col gap-3">
            <p className="self-end rounded-2xl rounded-br-sm bg-accent px-4 py-2.5 text-sm text-white sm:max-w-[80%] dark:text-[#0f1115]">
              {turn.question}
            </p>
            {turn.failed ? (
              <Notice tone="warn">{t.error}</Notice>
            ) : turn.response ? (
              <Answer turnId={turn.id} response={turn.response} locale={locale} />
            ) : (
              <p className="animate-pulse rounded-xl border border-line bg-surface px-4 py-3 text-sm text-muted">
                {t.thinking}
              </p>
            )}
          </article>
        ))}
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          submit(draft);
        }}
        className="sticky bottom-4 flex items-end gap-2 rounded-xl border border-line bg-surface p-2 shadow-sm"
      >
        <label htmlFor="question" className="sr-only">
          {t.placeholder}
        </label>
        <textarea
          id="question"
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              submit(draft);
            }
          }}
          placeholder={t.placeholder}
          maxLength={1000}
          className="field-sizing-content max-h-40 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-sm outline-none placeholder:text-muted"
        />
        <button
          type="submit"
          disabled={busy || !draft.trim()}
          className="h-10 rounded-lg bg-accent px-4 text-sm font-medium text-white transition-opacity disabled:opacity-40 dark:text-[#0f1115]"
        >
          {t.send}
        </button>
      </form>
      <div ref={bottom} />
    </div>
  );
}

function Answer({ turnId, response, locale }: { turnId: number; response: AskResponse; locale: Locale }) {
  const t = dictionaries[locale];

  if (response.status === "NO_SOURCE" || response.status === "NO_ANSWER") {
    return <Notice>{response.status === "NO_SOURCE" ? t.noSource : t.noAnswer}</Notice>;
  }

  const anchor = (n: number) => `source-${turnId}-${n}`;
  return (
    <div className="rounded-xl border border-line bg-surface">
      <div className="flex items-center gap-2 border-b border-line px-4 py-2">
        <span className="rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-medium text-accent">
          {t.aiGenerated}
        </span>
        <span className="ml-auto text-xs text-muted">
          {t.timing(response.retrievalMs, response.generationMs, response.model)}
        </span>
      </div>

      {response.status === "UNCITED" && (
        <div className="px-4 pt-3">
          <Notice tone="warn">{t.uncited}</Notice>
        </div>
      )}

      <p className="whitespace-pre-line px-4 py-3 text-[15px] leading-relaxed">
        {splitCitations(response.answer ?? "").map((part, i) =>
          typeof part === "number" ? (
            <a
              key={i}
              href={`#${anchor(part)}`}
              className="mx-0.5 rounded bg-accent-soft px-1 align-super text-[11px] font-semibold text-accent hover:underline"
            >
              {part}
            </a>
          ) : (
            <span key={i}>{part}</span>
          ),
        )}
      </p>

      {response.citations.length > 0 && (
        <div className="border-t border-line px-4 py-3">
          <h2 className="text-xs font-semibold tracking-wide text-muted uppercase">{t.sources}</h2>
          <ol className="mt-2 flex flex-col gap-2">
            {[...response.citations]
              .sort((a, b) => a.n - b.n)
              .map((citation) => (
                <Source key={citation.n} id={anchor(citation.n)} citation={citation} locale={locale} />
              ))}
          </ol>
        </div>
      )}
    </div>
  );
}

function Source({ id, citation, locale }: { id: string; citation: Citation; locale: Locale }) {
  const t = dictionaries[locale];
  const otherLanguage = citation.matchedLang !== backendLang[locale];
  return (
    <li id={id} className="flex scroll-mt-24 gap-3 text-sm target:rounded-md target:bg-accent-soft">
      <span className="mt-0.5 font-mono text-xs text-accent">[{citation.n}]</span>
      <div>
        <a href={citation.url} target="_blank" rel="noreferrer" className="font-medium hover:text-accent hover:underline">
          {citation.document} — {sourceLabel(citation, t.recital)}
          {citation.heading ? ` — ${citation.heading}` : ""}
          <span aria-hidden> ↗</span>
        </a>
        {otherLanguage && (
          <p className="text-xs text-muted">{t.foundIn(corpusLanguageName(locale, citation.matchedLang))}</p>
        )}
      </div>
    </li>
  );
}

function Notice({ children, tone = "info" }: { children: React.ReactNode; tone?: "info" | "warn" }) {
  return (
    <p
      role="status"
      className={`rounded-xl border px-4 py-3 text-sm ${
        tone === "warn" ? "border-warn/30 bg-warn-soft text-warn" : "border-line bg-surface text-muted"
      }`}
    >
      {children}
    </p>
  );
}
