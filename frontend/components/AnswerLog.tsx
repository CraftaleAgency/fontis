"use client";

import { useEffect, useState } from "react";
import { ApiError, recentAnswers, sourceLabel, type LogEntry, type Status } from "@/lib/api";
import { dictionaries, type Locale } from "@/lib/i18n";

// Must match fontis.log.retention-days in the backend.
const RETENTION_DAYS = 30;

const statusStyle: Record<Status, string> = {
  ANSWERED: "bg-accent-soft text-accent",
  NO_SOURCE: "bg-line text-muted",
  NO_ANSWER: "bg-line text-muted",
  UNCITED: "bg-warn-soft text-warn",
};

export default function AnswerLog({ locale }: { locale: Locale }) {
  const t = dictionaries[locale];
  const [entries, setEntries] = useState<LogEntry[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [forbidden, setForbidden] = useState(false);

  useEffect(() => {
    recentAnswers(100)
      .then(setEntries)
      .catch((error) => (error instanceof ApiError && error.status < 404 ? setForbidden(true) : setFailed(true)));
  }, []);

  const when = new Intl.DateTimeFormat(locale, { dateStyle: "short", timeStyle: "short" });

  return (
    <section>
      <h1 className="text-2xl font-semibold tracking-tight">{t.log.title}</h1>
      <p className="mt-2 max-w-2xl text-sm leading-relaxed text-muted">{t.log.intro(RETENTION_DAYS)}</p>

      {forbidden && <p className="mt-6 text-sm text-muted">{t.auth.adminOnly}</p>}
      {failed && <p className="mt-6 text-sm text-warn">{t.error}</p>}
      {entries?.length === 0 && <p className="mt-6 text-sm text-muted">{t.log.empty}</p>}

      {entries && entries.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded-xl border border-line bg-surface">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-line text-xs text-muted">
              <tr>
                <th className="px-4 py-2 font-medium">{t.log.when}</th>
                <th className="px-4 py-2 font-medium">{t.log.question}</th>
                <th className="px-4 py-2 font-medium">{t.log.outcome}</th>
                <th className="px-4 py-2 font-medium">{t.log.cited}</th>
                <th className="px-4 py-2 font-medium">{t.log.model}</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.id} className="border-b border-line align-top last:border-0">
                  <td className="px-4 py-2.5 whitespace-nowrap text-muted">{when.format(new Date(entry.createdAt))}</td>
                  <td className="px-4 py-2.5">{entry.question}</td>
                  <td className="px-4 py-2.5">
                    <span className={`rounded-full px-2 py-0.5 text-xs font-medium whitespace-nowrap ${statusStyle[entry.status]}`}>
                      {t.status[entry.status]}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-xs">
                    {entry.citations.map((c) => (
                      <a key={c.n} href={c.url || undefined} target="_blank" rel="noreferrer" className="block whitespace-nowrap hover:text-accent hover:underline">
                        {c.document} {sourceLabel(c, t.recital)}
                      </a>
                    ))}
                  </td>
                  <td className="px-4 py-2.5 font-mono text-xs whitespace-nowrap text-muted">{entry.model ?? "–"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
